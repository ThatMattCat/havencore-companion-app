package ai.havencore.companion.ui.components

import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Inline status row pinned to the top or bottom of a content area.
 * Severity drives the color role (info / warning / error / progress).
 *
 * Use for any persistent inline status: connection state, push
 * registration result, ping outcome. For modal scrims and dismissable
 * notifications, reach for the snackbar or sheet APIs instead.
 *
 * See `docs/design-system.md`.
 */
enum class BannerSeverity { Info, Warning, Error, Progress }

@Composable
fun Banner(
    severity: BannerSeverity,
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val (container, content) = severity.colors()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        tonalElevation = HavenTokens.Elevation.Level1,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = HavenTokens.Spacing.md,
                    vertical = HavenTokens.Spacing.sm,
                ),
            horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                severity == BannerSeverity.Progress -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun BannerSeverity.colors(): Pair<Color, Color> = when (this) {
    BannerSeverity.Info, BannerSeverity.Progress ->
        MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    BannerSeverity.Warning ->
        MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
    BannerSeverity.Error ->
        MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
}
