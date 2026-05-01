package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.chat.ConnectionUi
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionBanner(
    state: ConnectionUi,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = state !is ConnectionUi.Connected,
        modifier = modifier,
    ) {
        val text = when (state) {
            ConnectionUi.Connected -> "Connected"
            ConnectionUi.Connecting -> "Connecting…"
            is ConnectionUi.Reconnecting ->
                "Reconnecting (attempt ${state.attempt}, ${state.nextMs / 1000}s)…"
            is ConnectionUi.Failed -> "Connection failed: ${state.message}"
        }
        val showSpinner = state is ConnectionUi.Connecting || state is ConnectionUi.Reconnecting
        // Retry only makes sense once the supervisor has given up — which in
        // this app surfaces as ConnectionUi.Failed (clean close or invalid
        // baseUrl). Reconnecting handles its own backoff loop.
        val showRetry = onRetry != null && state is ConnectionUi.Failed

        Surface(
            tonalElevation = HavenTokens.Elevation.Level1,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = HavenTokens.Spacing.md,
                        vertical = HavenTokens.Spacing.xs,
                    ),
                horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showSpinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (showRetry) {
                    TextButton(onClick = onRetry!!) { Text("Retry") }
                }
            }
        }
    }
}
