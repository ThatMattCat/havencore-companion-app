package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
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
fun SummaryResetDivider(reason: String, summary: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HavenTokens.Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.Restore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Summary reset · ${reason.replace('_', ' ')}",
                style = MaterialTheme.typography.labelSmall,
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        if (summary.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = HavenTokens.Spacing.sm),
            ) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(HavenTokens.Spacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
