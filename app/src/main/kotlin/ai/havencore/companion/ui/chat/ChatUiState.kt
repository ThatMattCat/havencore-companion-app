package ai.havencore.companion.ui.chat

import ai.havencore.companion.data.DeviceAction
import ai.havencore.companion.data.DeviceActionResult
import ai.havencore.companion.net.ChatEvent
import kotlinx.serialization.json.JsonObject

data class ChatUiState(
    val agentLabel: String = "havencore",
    val sessionId: String? = null,
    val turns: List<TurnItem> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val turnInFlight: Boolean = false,
    val connection: ConnectionUi = ConnectionUi.Connecting,
    val voice: VoiceUi = VoiceUi.Idle,
    val autoSpeak: Boolean = false,
    val voiceError: String? = null,
)

sealed interface ConnectionUi {
    data object Connecting : ConnectionUi
    data object Connected : ConnectionUi
    data class Reconnecting(val attempt: Int, val nextMs: Long) : ConnectionUi
    data class Failed(val message: String) : ConnectionUi
}

sealed interface VoiceUi {
    data object Idle : VoiceUi
    data object Recording : VoiceUi
    data object Transcribing : VoiceUi
    data object Speaking : VoiceUi
}

sealed interface TurnItem {
    val key: Long

    data class UserTurn(
        override val key: Long,
        val text: String,
        val fromVoice: Boolean = false,
    ) : TurnItem

    data class AssistantTurn(
        override val key: Long,
        val events: List<TurnEvent> = emptyList(),
        val finalText: String? = null,
        val errorText: String? = null,
        val metric: ChatEvent.Metric? = null,
        val thinkingIteration: Int? = null,
    ) : TurnItem

    data class SummaryResetMarker(
        override val key: Long,
        val reason: String,
        val summary: String,
    ) : TurnItem
}

sealed interface TurnEvent {
    data class Reasoning(val content: String, val iteration: Int) : TurnEvent
    data class ToolPair(
        val id: String,
        val tool: String,
        val args: JsonObject,
        val result: String? = null,
        val ms: Int? = null,
    ) : TurnEvent
    data class DeviceActionItem(
        val action: String,
        val parsed: DeviceAction?,
        val result: DeviceActionResult,
    ) : TurnEvent
}
