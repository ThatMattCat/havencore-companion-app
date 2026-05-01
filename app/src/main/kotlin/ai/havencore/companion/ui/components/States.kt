package ai.havencore.companion.ui.components

import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Three canonical screen-level state surfaces. All center their
 * content vertically, all use a `HeroDisc` for the icon. Use these
 * (not bespoke layouts) for empty / loading / error states so the
 * app's screens feel like siblings.
 *
 * In-line errors (banners, field validation) belong in `Banner`, not
 * here. See `docs/design-system.md`.
 */

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
) {
    StateColumn(modifier = modifier) {
        HeroDisc(
            icon = icon,
            discColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = HavenTokens.Spacing.md),
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = HavenTokens.Spacing.xs),
            )
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    icon: ImageVector = Icons.Default.ErrorOutline,
    retryLabel: String = "Retry",
    modifier: Modifier = Modifier,
) {
    StateColumn(modifier = modifier) {
        HeroDisc(
            icon = icon,
            discColor = MaterialTheme.colorScheme.errorContainer,
            iconColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = HavenTokens.Spacing.md),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = HavenTokens.Spacing.sm),
        )
        if (onRetry != null) {
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = HavenTokens.Spacing.lg),
            ) {
                Text(retryLabel)
            }
        }
    }
}

@Composable
private fun StateColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(HavenTokens.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
