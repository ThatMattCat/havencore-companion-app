package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.theme.HavenBrandShapes
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = HavenBrandShapes.UserBubble,
            tonalElevation = HavenTokens.Elevation.Level1,
            modifier = Modifier.fillMaxWidth(0.78f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(
                    horizontal = HavenTokens.Spacing.md,
                    vertical = HavenTokens.Spacing.sm,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
