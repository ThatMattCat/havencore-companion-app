@file:Suppress("ConstructorParameterNaming", "PropertyName")

package ai.havencore.companion.net

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@OptIn(ExperimentalSerializationApi::class)
val HavenJson: Json = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
    coerceInputValues = true
}

// ---------------------------------------------------------------------------
// Client -> server frames
// ---------------------------------------------------------------------------

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SessionFrame(
    // The agent dispatches on data.get("type"); HavenJson has
    // encodeDefaults = false, so without @EncodeDefault the discriminator
    // would be silently dropped from the wire frame.
    @EncodeDefault val type: String = "session",
    val session_id: String? = null,
    val idle_timeout: Int? = null,
    val device_name: String? = null,
)

@Serializable
data class MessageFrame(val message: String)

// ---------------------------------------------------------------------------
// Server -> client events. Discriminator values are lowercase, matching the
// agent's orchestrator emission. New event types arriving from a future agent
// version are routed through ParsedFrame.Unknown rather than crashing.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ChatEvent {

    @Serializable
    @SerialName("session")
    data class Session(val session_id: String) : ChatEvent()

    @Serializable
    @SerialName("thinking")
    data class Thinking(val iteration: Int = 1) : ChatEvent()

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val tool: String,
        val args: JsonObject = JsonObject(emptyMap()),
        val id: String,
    ) : ChatEvent()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val tool: String,
        val result: String,
        val id: String,
        val ms: Int = 0,
    ) : ChatEvent()

    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val content: String,
        val iteration: Int = 1,
    ) : ChatEvent()

    @Serializable
    @SerialName("metric")
    data class Metric(
        val llm_ms: Int = 0,
        val tool_ms_total: Int = 0,
        val total_ms: Int = 0,
        val iterations: Int = 1,
        val tool_calls: List<ToolCallTiming> = emptyList(),
        val cache_read_tokens: Int? = null,
        val cache_creation_tokens: Int? = null,
    ) : ChatEvent()

    @Serializable
    @SerialName("done")
    data class Done(val content: String = "") : ChatEvent()

    // Renamed from "Error" to avoid shadowing kotlin.Error. SerialName keeps
    // the wire form intact.
    @Serializable
    @SerialName("error")
    data class Err(
        val error: String,
        val iterations: Int? = null,
    ) : ChatEvent()

    @Serializable
    @SerialName("summary_reset")
    data class SummaryReset(
        val reason: String,
        val summary: String = "",
    ) : ChatEvent()
}

@Serializable
data class ToolCallTiming(val name: String, val ms: Int)

// ---------------------------------------------------------------------------
// Forward-compatible parser: known events deserialize into ChatEvent;
// anything else is surfaced (with the raw JsonObject) for logging.
// ---------------------------------------------------------------------------

sealed class ParsedFrame {
    data class Known(val event: ChatEvent) : ParsedFrame()
    data class Unknown(val type: String, val raw: JsonObject) : ParsedFrame()
    data class Malformed(val raw: String, val cause: Throwable) : ParsedFrame()
}

private val knownEventTypes: Set<String> = setOf(
    "session",
    "thinking",
    "tool_call",
    "tool_result",
    "reasoning",
    "metric",
    "done",
    "error",
    "summary_reset",
)

fun parseChatFrame(raw: String): ParsedFrame {
    val element = try {
        HavenJson.parseToJsonElement(raw)
    } catch (t: Throwable) {
        return ParsedFrame.Malformed(raw, t)
    }
    if (element !is JsonObject) {
        return ParsedFrame.Malformed(raw, IllegalStateException("Expected JSON object"))
    }
    val type = (element["type"] as? JsonPrimitive)?.contentOrNull
        ?: return ParsedFrame.Malformed(raw, IllegalStateException("Missing 'type' field"))
    if (type !in knownEventTypes) {
        return ParsedFrame.Unknown(type, element)
    }
    return try {
        ParsedFrame.Known(HavenJson.decodeFromJsonElement(ChatEvent.serializer(), element))
    } catch (t: Throwable) {
        ParsedFrame.Malformed(raw, t)
    }
}

// ---------------------------------------------------------------------------
// REST DTOs (history list + resume)
// ---------------------------------------------------------------------------

@Serializable
data class ConversationSummary(
    val id: Int,
    val session_id: String,
    val created_at: String,
    val message_count: Int,
    val agent_name: String = "havencore",
    val metadata: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ListConversationsResponse(
    val conversations: List<ConversationSummary> = emptyList(),
    val limit: Int = 0,
    val offset: Int = 0,
)

@Serializable
data class ResumeResponse(
    val session_id: String,
    val resumed: Boolean,
    val message_count: Int = 0,
    val messages: List<JsonObject> = emptyList(),
)

fun ConversationSummary.rollingSummaryPreview(): String? =
    (metadata["rolling_summary"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun ConversationSummary.deviceName(): String? =
    (metadata["device_name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
