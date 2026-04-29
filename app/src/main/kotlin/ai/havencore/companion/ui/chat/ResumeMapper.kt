package ai.havencore.companion.ui.chat

import ai.havencore.companion.net.HavenJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Maps the OpenAI-format `messages` array returned by
 * `POST /api/conversations/{session_id}/resume` into the [TurnItem]s the
 * chat UI renders. Hydrated turns carry no thinkingIteration, no metric,
 * and no animations - they are immutable history above the live area.
 */
object ResumeMapper {

    private const val SUMMARY_PREFIX = "[Prior conversation summary]"

    fun toTurns(messages: List<JsonObject>, nextKey: () -> Long): List<TurnItem> {
        val out = mutableListOf<TurnItem>()
        var pendingAssistantIdx = -1

        for (msg in messages) {
            val role = (msg["role"] as? JsonPrimitive)?.contentOrNull ?: continue
            val contentStr = (msg["content"] as? JsonPrimitive)?.contentOrNull

            when (role) {
                "system" -> {
                    if (contentStr != null && contentStr.startsWith(SUMMARY_PREFIX)) {
                        val summary = contentStr.removePrefix(SUMMARY_PREFIX).trim()
                        out += TurnItem.SummaryResetMarker(
                            key = nextKey(),
                            reason = "prior_summary",
                            summary = summary,
                        )
                    }
                }

                "user" -> {
                    if (!contentStr.isNullOrEmpty()) {
                        out += TurnItem.UserTurn(nextKey(), contentStr)
                    }
                }

                "assistant" -> {
                    val toolCallsArr = msg["tool_calls"] as? JsonArray
                    val events = mutableListOf<TurnEvent>()
                    if (toolCallsArr != null) {
                        for (entry in toolCallsArr) {
                            val tc = entry as? JsonObject ?: continue
                            val id = (tc["id"] as? JsonPrimitive)?.contentOrNull ?: continue
                            val fn = tc["function"] as? JsonObject ?: continue
                            val name = (fn["name"] as? JsonPrimitive)?.contentOrNull ?: continue
                            val argsStr = (fn["arguments"] as? JsonPrimitive)?.contentOrNull ?: "{}"
                            val args = parseArgs(argsStr)
                            events += TurnEvent.ToolPair(id = id, tool = name, args = args)
                        }
                    }
                    val finalText = if (toolCallsArr != null) null else contentStr
                    val turn = TurnItem.AssistantTurn(
                        key = nextKey(),
                        events = events,
                        finalText = finalText,
                    )
                    pendingAssistantIdx = out.size
                    out += turn
                }

                "tool" -> {
                    val tcId = (msg["tool_call_id"] as? JsonPrimitive)?.contentOrNull
                    if (tcId != null && pendingAssistantIdx in out.indices) {
                        val at = out[pendingAssistantIdx] as? TurnItem.AssistantTurn ?: continue
                        val updated = at.events.toMutableList()
                        val idx = updated.indexOfLast { it is TurnEvent.ToolPair && it.id == tcId }
                        if (idx >= 0) {
                            val existing = updated[idx] as TurnEvent.ToolPair
                            updated[idx] = existing.copy(result = contentStr.orEmpty())
                            out[pendingAssistantIdx] = at.copy(events = updated)
                        }
                    }
                }
            }
        }
        return out
    }

    private fun parseArgs(raw: String): JsonObject {
        if (raw.isBlank()) return JsonObject(emptyMap())
        return try {
            HavenJson.parseToJsonElement(raw).jsonObject
        } catch (_: Throwable) {
            JsonObject(emptyMap())
        }
    }
}
