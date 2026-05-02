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

    companion object {
        fun fromEvent(event: ChatEvent.DeviceAction): DeviceAction? = when (event.action) {
            "set_alarm" -> parseSetAlarm(event.args, event.id)
            else -> null
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
}
