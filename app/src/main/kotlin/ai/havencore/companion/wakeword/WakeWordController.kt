package ai.havencore.companion.wakeword

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the wake-word engine lifecycle and the single-owner mic hand-off
 * between detection and post-wake capture.
 *
 * The openwakeword-android-kt lib holds its own AudioRecord while listening,
 * so we cannot fan a single mic stream out to both wake detection AND
 * post-wake STT. Instead the controller runs a small state machine:
 *
 *   Listening --(detection)--> Capturing --(VAD silence)--> Listening
 *
 * On every transition the previous mic owner releases AudioRecord before the
 * next one acquires it. The hand-off costs ~10–50 ms which is below the
 * perceptual threshold and well under the typical wake-utterance gap.
 *
 * Events are exposed on [events] as a SharedFlow so the foreground service
 * (notification updates, activity launches) and any UI observer can subscribe
 * without racing on the state field.
 */
class WakeWordController(
    private val ctx: Context,
    private val config: Config,
) {

    data class Config(
        val modelAssetPath: String,
        // Probability-space threshold (post-sigmoid). The hey_selene ONNX
        // export bakes a Sigmoid node in as the final op, matching the
        // openWakeWord / Home Assistant convention, so the lib's
        // `raw output > threshold` compare operates directly in probability
        // space — no client-side conversion needed.
        val threshold: Float,
        val vadAssetPath: String = SileroVad.DEFAULT_ASSET_PATH,
        val captureOutDir: File,
    )

    sealed interface Event {
        data object ListeningStarted : Event
        data object Stopped : Event
        data class Detected(val modelName: String, val score: Float) : Event
        data class Captured(val file: File, val durationMs: Long, val hadSpeech: Boolean) : Event
        data class Failed(val stage: String, val cause: Throwable) : Event
    }

    enum class Phase { Idle, Listening, Capturing }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _phase = MutableStateFlow(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var engine: WakeWordEngine? = null
    private var engineScope: CoroutineScope? = null
    private var vad: SileroVad? = null
    private var collectorJob: Job? = null
    private var tickJob: Job? = null

    fun start() {
        if (_phase.value != Phase.Idle) return
        scope.launch { startEngineLocked() }
    }

    fun stop() {
        scope.launch {
            stopEngineLocked()
            vad?.close()
            vad = null
            _phase.value = Phase.Idle
            _events.emit(Event.Stopped)
        }
    }

    private suspend fun startEngineLocked() {
        try {
            if (vad == null) {
                vad = SileroVad(ctx, config.vadAssetPath)
            }
            val model = WakeWordModel(
                name = MODEL_NAME,
                modelPath = config.modelAssetPath,
                threshold = config.threshold,
            )
            // Own the engine's coroutine scope so silent failures inside the
            // lib's audio collector surface as logged exceptions instead of
            // disappearing into the default uncaught handler (which Samsung
            // appears to swallow for non-system processes).
            val handler = CoroutineExceptionHandler { _, t ->
                Log.e(TAG, "engine coroutine crashed", t)
            }
            val newScope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default + handler,
            )
            val newEngine = WakeWordEngine(
                context = ctx,
                models = listOf(model),
                detectionCooldownMs = COOLDOWN_MS,
                scope = newScope,
            )
            engine = newEngine
            engineScope = newScope
            newEngine.start()
            collectorJob = scope.launch {
                newEngine.detections.collect { detection ->
                    Log.i(TAG, "wake detected ${detection.model.name} score=${detection.score}")
                    handleDetection(detection.model.name, detection.score)
                }
            }
            // Periodic heartbeat so we can tell whether the engine's audio
            // coroutine is alive at all when no detections fire.
            tickJob = scope.launch {
                while (isActive) {
                    delay(5_000)
                    Log.i(
                        TAG,
                        "heartbeat phase=${_phase.value} " +
                            "engineScopeActive=${newScope.coroutineContext[Job]?.isActive}",
                    )
                }
            }
            _phase.value = Phase.Listening
            _events.emit(Event.ListeningStarted)
        } catch (t: Throwable) {
            Log.e(TAG, "engine start failed", t)
            _events.emit(Event.Failed("engine_start", t))
            _phase.value = Phase.Idle
        }
    }

    private suspend fun stopEngineLocked() {
        tickJob?.cancel()
        tickJob = null
        collectorJob?.cancel()
        collectorJob = null
        engine?.runCatching { stop() }
        engine?.runCatching { release() }
        engine = null
        engineScope?.coroutineContext?.get(Job)?.cancel()
        engineScope = null
    }

    private fun handleDetection(modelName: String, score: Float) {
        scope.launch {
            _events.emit(Event.Detected(modelName, score))
            _phase.value = Phase.Capturing
            stopEngineLocked()
            // Small warm-up so the lib's coroutine cancellation can register
            // before we open a new AudioRecord on the same source. The
            // capture session has its own warm-up + retry loop that handles
            // the slow-release case on Samsung devices, so we don't need a
            // large blanket delay here.
            delay(POST_DETECTION_WARMUP_MS)
            val vadRef = vad
            if (vadRef == null) {
                _events.emit(Event.Failed("vad_missing", IllegalStateException("VAD not loaded")))
                _phase.value = Phase.Listening
                startEngineLocked()
                return@launch
            }
            val capture = WakeCaptureSession(ctx, vadRef)
            val result = capture.capture(outDir = config.captureOutDir)
            result.onSuccess { res ->
                _events.emit(Event.Captured(res.file, res.durationMs, res.hadSpeech))
            }.onFailure { t ->
                _events.emit(Event.Failed("capture", t))
                // A vad_degraded failure means the ONNX session is wedged for
                // the rest of its lifetime — drop the reference so the next
                // startEngineLocked() rebuilds it.
                if (t.message == "vad_degraded") {
                    runCatching { vad?.close() }
                    vad = null
                }
            }
            _phase.value = Phase.Listening
            startEngineLocked()
        }
    }

    companion object {
        private const val TAG = "WakeWord:Ctrl"
        private const val MODEL_NAME = "hey_selene"
        // 1.5 s suppresses bursty re-triggers without colliding with the
        // shortest plausible "hey selene + request" cadence.
        private const val COOLDOWN_MS = 1500L
        // Brief gap between engine.stop() and opening the capture mic, just
        // enough for the lib's coroutine cancellation to register. The real
        // silent-handoff mitigation is the retry loop in WakeCaptureSession.
        private const val POST_DETECTION_WARMUP_MS = 50L
    }
}
