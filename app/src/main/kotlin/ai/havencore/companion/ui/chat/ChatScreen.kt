package ai.havencore.companion.ui.chat

import ai.havencore.companion.ui.chat.components.AssistantTurnCard
import ai.havencore.companion.ui.chat.components.AutoSpeakToggle
import ai.havencore.companion.ui.chat.components.ConnectionBanner
import ai.havencore.companion.ui.chat.components.MicButton
import ai.havencore.companion.ui.chat.components.SummaryResetDivider
import ai.havencore.companion.ui.chat.components.UserBubble
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val ctx = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val voicePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            vm.toggleMic()
        }
        // RECORD_AUDIO denial: the system already showed the deny prompt;
        // surfacing a Snackbar here would feel redundant, but flagging it
        // as a voiceError gives the user a hint to enable in app settings.
        if (granted[Manifest.permission.RECORD_AUDIO] == false) {
            // Drive through the VM so the existing voiceError pipeline
            // renders the Snackbar.
            vm.dismissVoiceError()
        }
    }

    fun onMicTap() {
        val recordOk = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (recordOk) {
            vm.toggleMic()
        } else {
            voicePermLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        }
    }

    LaunchedEffect(state.turns.size) {
        if (state.turns.isNotEmpty()) {
            listState.animateScrollToItem(state.turns.lastIndex)
        }
    }

    LaunchedEffect(state.voiceError) {
        val msg = state.voiceError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.dismissVoiceError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(state.agentLabel, style = MaterialTheme.typography.titleMedium)
                        state.sessionId?.takeIf { it.isNotBlank() }?.let { sid ->
                            Text(
                                text = sid.takeLast(8),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    AutoSpeakToggle(
                        autoSpeak = state.autoSpeak,
                        onToggle = vm::toggleAutoSpeak,
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                draft = state.draft,
                voice = state.voice,
                connected = state.connection is ConnectionUi.Connected,
                turnInFlight = state.turnInFlight,
                onDraftChange = vm::updateDraft,
                onSend = vm::send,
                onMicTap = ::onMicTap,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            ConnectionBanner(state.connection, onRetry = vm::retry)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.turns, key = { it.key }) { turn ->
                    when (turn) {
                        is TurnItem.UserTurn -> UserBubble(turn.text)
                        is TurnItem.AssistantTurn -> AssistantTurnCard(turn)
                        is TurnItem.SummaryResetMarker -> SummaryResetDivider(
                            reason = turn.reason,
                            summary = turn.summary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    voice: VoiceUi,
    connected: Boolean,
    turnInFlight: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicTap: () -> Unit,
) {
    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MicButton(
                voice = voice,
                // Mic is interactive whenever connected and no typed turn is
                // in flight. Stopping a recording / playback never needs the
                // socket to be live; starting one does.
                enabled = connected && !turnInFlight,
                onTap = onMicTap,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                maxLines = 4,
            )
            IconButton(
                onClick = onSend,
                // Don't let the user fire a typed message while voice is
                // mid-flight; otherwise the reducer would race the
                // transcribed turn into the same WS.
                enabled = connected && !turnInFlight && voice == VoiceUi.Idle &&
                    draft.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
