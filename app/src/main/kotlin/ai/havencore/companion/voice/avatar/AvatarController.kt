package ai.havencore.companion.voice.avatar

import ai.havencore.companion.audio.TtsPlayer
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single state-flow surface consumed by [AvatarOverlayService]'s WebView
 * bridge. Owns the [AvatarUiState] and merges in mouth-shape updates from
 * the [VisemeScheduler]; phase + caption transitions are pushed in
 * imperatively by [VoiceTurnRunner] and the overlay service.
 *
 * Activity tracking ([lastActivityAtMs]) is bumped on every imperative
 * call and on every viseme tick during Speaking, so the [IdleWatcher]
 * can dismiss the overlay after a quiescent stretch.
 *
 * Expression-source policy (v1 of the avatar overlay): expressions are
 * phase-tied here. The placeholder Hiyori model has no .exp3 files, so
 * setExpression() in JS is a soft no-op; the seam is reserved for a
 * future server-side affect hint.
 */
class AvatarController(
    private val visemeScheduler: VisemeScheduler,
    private val ttsPlayer: TtsPlayer,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AvatarUiState())
    val state: StateFlow<AvatarUiState> = _state.asStateFlow()

    @Volatile
    var lastActivityAtMs: Long = SystemClock.elapsedRealtime()
        private set

    private var visemeCollector: Job? = null
    private var ttsStateCollector: Job? = null

    fun start() {
        if (visemeCollector?.isActive != true) {
            visemeCollector = scope.launch {
                visemeScheduler.currentShape.collect { shape ->
                    bumpActivity()
                    _state.update {
                        it.copy(mouthOpenY = shape.openY, mouthForm = shape.form)
                    }
                }
            }
        }
        if (ttsStateCollector?.isActive != true) {
            ttsStateCollector = scope.launch {
                ttsPlayer.state.collect { _ -> bumpActivity() }
            }
        }
    }

    fun setPhase(phase: AvatarPhase) {
        bumpActivity()
        _state.update {
            it.copy(phase = phase, expression = expressionFor(phase))
        }
    }

    fun setCaption(text: String?) {
        bumpActivity()
        _state.update { it.copy(caption = text) }
    }

    fun setError(message: String) {
        bumpActivity()
        _state.update {
            it.copy(
                phase = AvatarPhase.Error,
                expression = expressionFor(AvatarPhase.Error),
                caption = message,
            )
        }
    }

    fun reset() {
        bumpActivity()
        _state.value = AvatarUiState()
    }

    fun release() {
        visemeCollector?.cancel()
        ttsStateCollector?.cancel()
        scope.cancel()
    }

    private fun bumpActivity() {
        lastActivityAtMs = SystemClock.elapsedRealtime()
    }

    private fun expressionFor(phase: AvatarPhase): String = when (phase) {
        AvatarPhase.Idle -> "neutral"
        AvatarPhase.Listening -> "alert"
        AvatarPhase.Thinking -> "neutral"
        AvatarPhase.Speaking -> "happy"
        AvatarPhase.Error -> "sad"
    }
}
