package ai.havencore.companion.voice

/**
 * State for the assist overlay. The session reduces incoming WS frames
 * and mic / TTS events into one of these phases; the Compose surface
 * picks the body to render off [phase] and the optional payload fields.
 */
data class AssistUiState(
    val phase: Phase = Phase.Connecting,
    val transcript: String = "",
    val reply: String = "",
    val toolCount: Int = 0,
    val actionCount: Int = 0,
    val errorMessage: String? = null,
)

sealed interface Phase {
    data object Connecting : Phase
    data object Listening : Phase
    data object Transcribing : Phase
    data object Thinking : Phase
    data object Replying : Phase
    data object NoSpeech : Phase
    data object PermissionMissing : Phase
    data object Error : Phase
}
