package ai.havencore.companion.data

import ai.havencore.companion.net.ChatEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsed, type-safe device action ready to be dispatched on the device.
 * The agent emits a generic ChatEvent.DeviceAction(action, args, ...);
 * [fromEvent] maps the small set of known actions onto these classes.
 * Unknown action names map to null so the UI can render an inert
 * "(unsupported)" card instead of crashing.
 */
sealed interface DeviceAction {

    val sourceId: String?

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String?,
        val daysOfWeek: List<Int> = emptyList(),
        val skipUi: Boolean = true,
        val vibrate: Boolean = true,
        override val sourceId: String? = null,
    ) : DeviceAction

    /**
     * Marker for any device action that captures a photo and uploads it
     * back to the agent. The dispatcher's capture+upload primitive treats
     * all variants uniformly; the variant only differs in chat-card
     * presentation and which per-tool toggle gates it.
     */
    sealed interface CameraCapture : DeviceAction {
        val toolCallId: String
    }

    /**
     * Capture a photo with the device's camera and upload the bytes to the
     * agent's `/api/companion/upload` keyed by [toolCallId]. The agent has
     * a tool call awaiting the upload future; the upload arrival resolves
     * it and the LLM gets back an `image_url`. [reason], if present, is a
     * short user-facing rationale supplied by the LLM (e.g. "to identify
     * the plant") — surfaced in the chat card.
     */
    data class TakePhoto(
        override val toolCallId: String,
        val reason: String?,
        override val sourceId: String? = null,
    ) : CameraCapture

    /**
     * Capture a photo and let the agent ask the vision model what's in it.
     * [hint], if present, narrows the identification ("plant", "bird").
     * Same wire pattern as [TakePhoto]; the agent does the vision chaining.
     */
    data class IdentifyObjectInPhoto(
        override val toolCallId: String,
        val hint: String?,
        override val sourceId: String? = null,
    ) : CameraCapture

    /**
     * Capture a photo and let the agent OCR the visible text. No client-side
     * args; the prompt is server-determined.
     */
    data class ReadTextFromImage(
        override val toolCallId: String,
        override val sourceId: String? = null,
    ) : CameraCapture

    companion object {
        fun fromEvent(event: ChatEvent.DeviceAction): DeviceAction? = when (event.action) {
            "set_alarm" -> parseSetAlarm(event.args, event.id)
            "take_photo" -> parseTakePhoto(event.args, event.id)
            "identify_object_in_photo" -> parseIdentifyObjectInPhoto(event.args, event.id)
            "read_text_from_image" -> parseReadTextFromImage(event.id)
            else -> null
        }

        private fun parseTakePhoto(args: JsonObject, id: String?): TakePhoto? {
            val toolCallId = id?.takeIf { it.isNotBlank() } ?: return null
            val reason = (args["reason"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            return TakePhoto(toolCallId = toolCallId, reason = reason, sourceId = id)
        }

        private fun parseIdentifyObjectInPhoto(
            args: JsonObject,
            id: String?,
        ): IdentifyObjectInPhoto? {
            val toolCallId = id?.takeIf { it.isNotBlank() } ?: return null
            val hint = (args["hint"] as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            return IdentifyObjectInPhoto(
                toolCallId = toolCallId, hint = hint, sourceId = id,
            )
        }

        private fun parseReadTextFromImage(id: String?): ReadTextFromImage? {
            val toolCallId = id?.takeIf { it.isNotBlank() } ?: return null
            return ReadTextFromImage(toolCallId = toolCallId, sourceId = id)
        }

        private fun parseSetAlarm(args: JsonObject, id: String?): SetAlarm? {
            val hour = (args["hour"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                ?: return null
            val minute = (args["minute"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null

            val label = (args["label"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

            val daysOfWeek = args["days_of_week"]?.let { el ->
                runCatching {
                    el.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
                        .filter { it in 1..7 }
                }.getOrDefault(emptyList())
            } ?: emptyList()

            return SetAlarm(
                hour = hour,
                minute = minute,
                label = label,
                daysOfWeek = daysOfWeek,
                sourceId = id,
            )
        }
    }
}

/**
 * Outcome of [ai.havencore.companion.device.DeviceActionDispatcher.dispatch].
 * The UI uses this to render the success / failure state of a device-action
 * card. [Unsupported] is the case where the wire-event arrived but no parser
 * matched.
 */
sealed interface DeviceActionResult {
    data object Fired : DeviceActionResult
    data object NoHandler : DeviceActionResult
    data object Unsupported : DeviceActionResult
    data class Failed(val reason: String) : DeviceActionResult

    // Camera-tool flow has more states than fire-and-forget intents:
    //   InProgress    — capture activity launched, waiting on user
    //   Uploading     — bytes returned, POSTing to /api/companion/upload
    //   Uploaded      — server acknowledged, future resolved on agent side
    //   Disabled      — master toggle off in Settings; capture never fired
    //   Cancelled     — user backed out of the camera capture
    data object InProgress : DeviceActionResult
    data object Uploading : DeviceActionResult
    data object Uploaded : DeviceActionResult
    data object Disabled : DeviceActionResult
    data object Cancelled : DeviceActionResult
}
