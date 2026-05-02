package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.data.DeviceAction
import ai.havencore.companion.data.DeviceActionResult
import ai.havencore.companion.ui.chat.TurnEvent
import ai.havencore.companion.ui.components.AccentDisc
import ai.havencore.companion.ui.theme.HavenTokens
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import java.util.Calendar

/**
 * Inline row inside an assistant turn for a fired device action. Flat
 * (no nested card) so the parent assistant Surface owns the visual
 * frame, mirroring [ToolCallRow]. Severity drives the leading disc's
 * tint: success uses primaryContainer; failure uses errorContainer.
 */
@Composable
fun DeviceActionRow(item: TurnEvent.DeviceActionItem, modifier: Modifier = Modifier) {
    val display = displayFor(item)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
    ) {
        AccentDisc(
            icon = display.icon,
            discColor = display.discColor,
            iconColor = display.iconColor,
            contentDescription = null,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            display.subtitle?.let { sub ->
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private data class RowDisplay(
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val discColor: Color,
    val iconColor: Color,
)

@Composable
private fun displayFor(item: TurnEvent.DeviceActionItem): RowDisplay {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val parsed = item.parsed
    val title = when {
        item.result is DeviceActionResult.Unsupported ->
            "Action not supported on this device"
        parsed is DeviceAction.SetAlarm && item.result is DeviceActionResult.Fired ->
            "Set alarm for ${formatTime(context, parsed.hour, parsed.minute)}"
        parsed is DeviceAction.SetAlarm && item.result is DeviceActionResult.NoHandler ->
            "Couldn't set alarm"
        parsed is DeviceAction.SetAlarm && item.result is DeviceActionResult.Failed ->
            "Couldn't set alarm"
        else -> "Device action"
    }
    val subtitle = when (val r = item.result) {
        is DeviceActionResult.NoHandler -> "No alarm app available"
        is DeviceActionResult.Failed -> r.reason
        is DeviceActionResult.Unsupported -> item.action
        is DeviceActionResult.Fired -> (parsed as? DeviceAction.SetAlarm)?.label
    }
    val isFailure = item.result is DeviceActionResult.NoHandler ||
        item.result is DeviceActionResult.Failed ||
        item.result is DeviceActionResult.Unsupported
    val (disc, onDisc) = if (isFailure) {
        cs.errorContainer to cs.onErrorContainer
    } else {
        cs.primaryContainer to cs.onPrimaryContainer
    }
    val icon = when {
        isFailure && parsed !is DeviceAction.SetAlarm -> Icons.Default.ErrorOutline
        parsed is DeviceAction.SetAlarm -> Icons.Default.Alarm
        else -> Icons.Default.Build
    }
    return RowDisplay(
        title = title,
        subtitle = subtitle,
        icon = icon,
        discColor = disc,
        iconColor = onDisc,
    )
}

private fun formatTime(context: android.content.Context, hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(cal.time)
}
