package ai.havencore.companion.ui.history

import ai.havencore.companion.net.ConversationSummary
import ai.havencore.companion.net.deviceName
import ai.havencore.companion.net.rollingSummaryPreview
import ai.havencore.companion.ui.components.AccentDisc
import ai.havencore.companion.ui.components.AnimatedSwap
import ai.havencore.companion.ui.components.EmptyState
import ai.havencore.companion.ui.components.ErrorState
import ai.havencore.companion.ui.components.LoadingState
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conversations") },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.clearLastSessionForNewChat()
                    onNew()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New conversation") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = HavenTokens.Elevation.Level2,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            AnimatedSwap(
                targetState = state,
                contentKey = { it::class },
                label = "history-state",
            ) { s ->
                when (s) {
                    HistoryListState.Loading -> LoadingState()
                    HistoryListState.Empty -> EmptyState(
                        icon = Icons.Default.ChatBubbleOutline,
                        title = "No conversations yet",
                        body = "Tap + to start one.",
                    )
                    is HistoryListState.Failed -> ErrorState(
                        title = "Couldn't load conversations.",
                        message = s.message,
                        onRetry = vm::refresh,
                    )
                    is HistoryListState.Loaded -> LoadedList(
                        rows = s.rows,
                        refreshing = false,
                        onRefresh = vm::refresh,
                        onOpen = onOpen,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedList(
    rows: List<ConversationSummary>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows, key = { it.id }) { row ->
                HistoryRow(row = row, onClick = { onOpen(row.session_id) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = HavenTokens.Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(row: ConversationSummary, onClick: () -> Unit) {
    val device = row.deviceName()
    val rolling = row.rollingSummaryPreview()
    val firstLine = rolling
        ?.lineSequence()
        ?.firstOrNull()
        ?.take(120)
        ?.takeIf { it.isNotBlank() }

    val supportingMeta = buildString {
        append(relativeTime(row.created_at))
        append(" · ")
        append("${row.message_count} msg")
        if (device != null) {
            append(" · ")
            append(device)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = HavenTokens.Spacing.lg,
                vertical = HavenTokens.Spacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.md),
    ) {
        AccentDisc(
            icon = Icons.Default.ChatBubbleOutline,
            discColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.agent_name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = relativeTime(row.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = firstLine ?: supportingMeta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (firstLine != null) {
                Text(
                    text = supportingMeta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

private fun relativeTime(iso: String): String {
    val parsed = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val seconds = Duration.between(parsed, Instant.now()).seconds
    return when {
        seconds < 60 -> "just now"
        seconds < 3_600 -> "${seconds / 60} min ago"
        seconds < 86_400 -> "${seconds / 3_600} h ago"
        seconds < 7 * 86_400 -> "${seconds / 86_400} d ago"
        else -> {
            val zoned = parsed.atZone(ZoneId.systemDefault()).toLocalDate()
            DateTimeFormatter.ofPattern("MMM d").format(zoned)
        }
    }
}
