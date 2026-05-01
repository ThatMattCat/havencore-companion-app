package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.net.ChatEvent
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetricChips(metric: ChatEvent.Metric, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggle = { expanded = !expanded }

    Column(modifier = modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.xs)) {
            AssistChip(
                onClick = toggle,
                label = { Text("${metric.total_ms} ms total") },
            )
            AssistChip(
                onClick = toggle,
                label = { Text("${metric.llm_ms} ms LLM") },
            )
            AssistChip(
                onClick = toggle,
                label = { Text("${metric.tool_ms_total} ms tools") },
            )
            metric.cache_read_tokens?.takeIf { it > 0 }?.let { hits ->
                AssistChip(
                    onClick = toggle,
                    label = { Text("$hits cache hit") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                )
            }
        }
        if (expanded && metric.tool_calls.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = HavenTokens.Spacing.xs)) {
                metric.tool_calls.forEach { tc ->
                    Text(
                        "• ${tc.name}: ${tc.ms} ms",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
