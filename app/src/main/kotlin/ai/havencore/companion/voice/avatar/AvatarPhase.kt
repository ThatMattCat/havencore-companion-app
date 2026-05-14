package ai.havencore.companion.voice.avatar

/**
 * Lifecycle phases the avatar overlay can be in. Separate from
 * [ai.havencore.companion.voice.Phase] (the assist-overlay enum) — the
 * avatar surface has no Transcribing / NoSpeech / PermissionMissing
 * states because those are upstream of the overlay (the wake-word service
 * gates capture before this surface ever shows).
 */
enum class AvatarPhase {
    Idle,
    Listening,
    Thinking,
    Speaking,
    Error,
}

/**
 * Single state object consumed by the WebView bridge. Phase 4's
 * AvatarController reduces multiple event streams (wake / chat / TTS /
 * viseme) into a [StateFlow] of this type.
 *
 * [mouthOpenY] (0..1) maps to Live2D `ParamMouthOpenY`. [mouthForm] (-1..1)
 * maps to `ParamMouthForm`. Both ignored unless [phase] is Speaking — the
 * JS side holds the mouth closed otherwise.
 */
data class AvatarUiState(
    val phase: AvatarPhase = AvatarPhase.Idle,
    val expression: String = "neutral",
    val mouthOpenY: Float = 0f,
    val mouthForm: Float = 0f,
    val caption: String? = null,
)
