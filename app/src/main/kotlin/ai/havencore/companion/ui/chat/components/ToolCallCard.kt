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
import androidx.compose.material3.OutlinedCard
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

@Composable
fun ToolCallCard(pair: TurnEvent.ToolPair, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(pair.id) { mutableStateOf(false) }

    val verb = if (pair.result == null) "Calling" else "Result from"
    val icon = if (pair.result == null) Icons.Default.Build else Icons.Default.CheckCircle

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(HavenTokens.Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    "$verb ${pair.tool}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                pair.ms?.let {
                    Text("$it ms", style = MaterialTheme.typography.labelSmall)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Spacer(Modifier.size(HavenTokens.Spacing.sm))
                Text("args", style = MaterialTheme.typography.labelMedium)
                MonoBlock(prettyJson(pair.args))
                pair.result?.let { res ->
                    Spacer(Modifier.size(HavenTokens.Spacing.sm))
                    Text("result", style = MaterialTheme.typography.labelMedium)
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
}

@Composable
private fun MonoBlock(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private val prettyJson = Json { prettyPrint = true }

@Composable
private fun prettyJson(args: JsonObject): String =
    remember(args) { prettyJson.encodeToString(JsonObject.serializer(), args) }
