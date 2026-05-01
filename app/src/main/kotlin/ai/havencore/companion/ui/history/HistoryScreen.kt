package ai.havencore.companion.ui.history

import ai.havencore.companion.net.ConversationSummary
import ai.havencore.companion.net.deviceName
import ai.havencore.companion.net.rollingSummaryPreview
import ai.havencore.companion.ui.components.HeroDisc
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
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
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val s = state) {
                HistoryListState.Loading -> CenteredSpinner()
                HistoryListState.Empty -> EmptyState()
                is HistoryListState.Failed -> ErrorState(message = s.message, onRetry = vm::refresh)
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

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(HavenTokens.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeroDisc(
            icon = Icons.Default.ChatBubbleOutline,
            discColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            "No conversations yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = HavenTokens.Spacing.md),
        )
        Text(
            "Tap + to start one",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = HavenTokens.Spacing.xs),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(HavenTokens.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeroDisc(
            icon = Icons.Default.ErrorOutline,
            discColor = MaterialTheme.colorScheme.errorContainer,
            iconColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = "Couldn't load conversations.",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = HavenTokens.Spacing.md),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = HavenTokens.Spacing.sm),
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = HavenTokens.Spacing.lg)) {
            Text("Retry")
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
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryRow(row: ConversationSummary, onClick: () -> Unit) {
    val device = row.deviceName()
    val supporting = buildString {
        append(relativeTime(row.created_at))
        append(" · ")
        append("${row.message_count} msg")
        if (device != null) {
            append(" · ")
            append(device)
        }
    }
    val rolling = row.rollingSummaryPreview()

    ListItem(
        headlineContent = { Text(row.agent_name) },
        supportingContent = {
            Column {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (rolling != null) {
                    val firstLine = rolling.lineSequence().firstOrNull().orEmpty().take(120)
                    if (firstLine.isNotBlank()) {
                        Text(
                            text = firstLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open",
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
