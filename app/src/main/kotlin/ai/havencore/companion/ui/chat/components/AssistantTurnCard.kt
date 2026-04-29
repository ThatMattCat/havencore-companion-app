package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.chat.TurnEvent
import ai.havencore.companion.ui.chat.TurnItem
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (turn.finalText == null && turn.errorText == null) {
            ThinkingRow(turn.thinkingIteration)
        }
        for (event in turn.events) {
            when (event) {
                is TurnEvent.ToolPair -> ToolCallCard(event)
                is TurnEvent.Reasoning -> ReasoningCard(event)
            }
        }
        turn.finalText?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
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
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        turn.metric?.let { MetricChips(it) }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
