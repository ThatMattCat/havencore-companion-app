package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.chat.TurnEvent
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val RESULT_DISPLAY_CAP = 7990

/**
 * Inline expandable row for a tool call inside an assistant turn.
 * Visually flat — no nested card — so the parent assistant card owns
 * the visual frame. Tap toggles args/result detail.
 */
@Composable
fun ToolCallRow(pair: TurnEvent.ToolPair, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(pair.id) { mutableStateOf(false) }

    val verb = if (pair.result == null) "Calling" else "Result from"
    val icon = if (pair.result == null) Icons.Default.Build else Icons.Default.CheckCircle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$verb ${pair.tool}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            pair.ms?.let {
                Text(
                    text = "$it ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.size(HavenTokens.Spacing.sm))
            Text(
                text = "args",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MonoBlock(prettyJson(pair.args))
            pair.result?.let { res ->
                Spacer(Modifier.size(HavenTokens.Spacing.sm))
                Text(
                    text = "result",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val display = if (res.length > RESULT_DISPLAY_CAP) {
                    res.take(RESULT_DISPLAY_CAP) + "\n…(truncated)"
                } else {
                    res
                }
                MonoBlock(display)
            }
        }
    }
}

@Composable
private fun MonoBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = HavenTokens.Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .padding(HavenTokens.Spacing.sm),
        ) {
            Text(
                text = text,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val prettyJson = Json { prettyPrint = true }

@Composable
private fun prettyJson(args: JsonObject): String =
    remember(args) { prettyJson.encodeToString(JsonObject.serializer(), args) }
