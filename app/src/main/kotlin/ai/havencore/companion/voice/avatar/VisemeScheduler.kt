package ai.havencore.companion.voice.avatar

import ai.havencore.companion.audio.TtsPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Schedules Live2D mouth shapes against the active [TtsPlayer]'s playback
 * position, using a Rhubarb [VisemeTimeline] as the source of truth.
 *
 * Lifecycle:
 * - Construct once per AppContainer, keep around.
 * - Call [setTimeline] when a new TTS clip begins playing (or with null
 *   when no timeline is available — e.g. server lacks Rhubarb).
 * - Observers collect [currentShape] from a coroutine; the scheduler
 *   internally subscribes to [TtsPlayer.currentPositionMs] and computes
 *   the appropriate shape per tick.
 *
 * Output emits [MouthShape.Closed] whenever no timeline is active or the
 * player is not in [TtsPlayer.State.Playing].
 */
class VisemeScheduler(private val ttsPlayer: TtsPlayer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentShape = MutableStateFlow(MouthShape.Closed)
    val currentShape: StateFlow<MouthShape> = _currentShape.asStateFlow()

    private var timeline: VisemeTimeline? = null
    private var pollJob: Job? = null

    // Lerp state — interpolate from [fromShape] toward [toShape] over
    // [LERP_DURATION_MS] starting at [lerpStartedAt]. Without this the
    // mouth snaps between cues every ~80 ms which reads as "fake".
    private var fromShape = MouthShape.Closed
    private var toShape = MouthShape.Closed
    private var lerpStartedAt = 0L
    private var lastCueValue: String? = null

    fun setTimeline(t: VisemeTimeline?) {
        timeline = t
        lastCueValue = null
        fromShape = MouthShape.Closed
        toShape = MouthShape.Closed
        lerpStartedAt = System.nanoTime() / 1_000_000L
        android.util.Log.i(
            "VisemeSched",
            "setTimeline: ${if (t == null) "null (mouth held closed)" else "${t.mouthCues.size} cues, duration=${t.metadata.duration}s"}",
        )
        if (t == null) {
            _currentShape.value = MouthShape.Closed
            return
        }
        ensurePolling()
    }

    fun release() {
        pollJob?.cancel()
        pollJob = null
        scope.cancel()
    }

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            ttsPlayer.currentPositionMs.collect { posMs -> tick(posMs) }
        }
    }

    private fun tick(posMs: Long) {
        val tl = timeline
        val isPlaying = ttsPlayer.state.value is TtsPlayer.State.Playing
        if (tl == null || !isPlaying) {
            _currentShape.value = MouthShape.Closed
            return
        }

        val cue = tl.cueAt(posMs / 1000.0)
        val targetValue = cue?.value ?: "X"
        val target = SHAPES[targetValue] ?: MouthShape.Closed

        val now = System.nanoTime() / 1_000_000L
        if (lastCueValue != targetValue) {
            // Start a new lerp from where we currently are toward the
            // newly-active cue's target shape.
            fromShape = _currentShape.value
            toShape = target
            lerpStartedAt = now
            lastCueValue = targetValue
        }

        val t = ((now - lerpStartedAt).toFloat() / LERP_DURATION_MS).coerceIn(0f, 1f)
        _currentShape.value = MouthShape(
            openY = lerp(fromShape.openY, toShape.openY, t),
            form = lerp(fromShape.form, toShape.form, t),
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private companion object {
        const val LERP_DURATION_MS = 40f

        // Preston-Blair viseme → Live2D mouth params. Numbers are starting
        // points from the plan; tune in flight against the actual rig.
        val SHAPES: Map<String, MouthShape> = mapOf(
            "X" to MouthShape(0.0f, 0.0f),    // silence
            "A" to MouthShape(0.85f, 0.0f),   // ah
            "B" to MouthShape(0.25f, 0.7f),   // ee/i
            "C" to MouthShape(0.5f, 0.3f),    // eh/I/EI
            "D" to MouthShape(0.7f, 0.1f),    // aa/AY
            "E" to MouthShape(0.6f, -0.5f),   // oh/O
            "F" to MouthShape(0.3f, -0.8f),   // oo/U
            "G" to MouthShape(0.1f, 0.2f),    // f/v
            "H" to MouthShape(0.4f, 0.0f),    // l
        )
    }
}
