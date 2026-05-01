package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.chat.TurnEvent
import ai.havencore.companion.ui.chat.TurnItem
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AssistantTurnCard(turn: TurnItem.AssistantTurn, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        tonalElevation = HavenTokens.Elevation.Level1,
    ) {
        Column(
            modifier = Modifier.padding(HavenTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
        ) {
            if (turn.finalText == null && turn.errorText == null) {
                ThinkingRow(turn.thinkingIteration)
            }
            for (event in turn.events) {
                when (event) {
                    is TurnEvent.ToolPair -> ToolCallRow(event)
                    is TurnEvent.Reasoning -> ReasoningRow(event)
                }
            }
            turn.finalText?.takeIf { it.isNotBlank() }?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            turn.errorText?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(HavenTokens.Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(HavenTokens.Spacing.sm))
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            turn.metric?.let { metric ->
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = HavenTokens.Spacing.xs),
                )
                MetricChips(metric)
            }
        }
    }
}

@Composable
private fun ThinkingRow(iteration: Int?) {
    val label = if (iteration != null && iteration > 1) {
        "Retrying tool call (iteration $iteration)…"
    } else {
        "Thinking…"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
