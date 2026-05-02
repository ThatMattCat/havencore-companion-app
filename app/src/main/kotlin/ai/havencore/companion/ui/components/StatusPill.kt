package ai.havencore.companion.ui.components

import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Pill-shaped status indicator. Canonical instance is the assist
 * overlay's phase pill (Listening / Thinking / Speaking …).
 *
 * Pass an explicit container/content color pair so the caller chooses
 * the semantic meaning (primaryContainer = active, secondaryContainer
 * = neutral, tertiaryContainer = noteworthy, errorContainer = wrong).
 *
 * See `docs/design-system.md` for the role usage guide.
 */
@Composable
fun StatusPill(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = HavenTokens.Radius.pill,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = HavenTokens.Spacing.md,
                vertical = HavenTokens.Spacing.xs,
            ),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
