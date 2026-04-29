package ai.havencore.companion.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

/**
 * Bottom-sheet assist overlay. The outer Box paints a 40 % black scrim
 * across the whole window (the session Window is sized MATCH_PARENT) and
 * dismisses on any tap. The inner Surface is anchored at the bottom,
 * fixed at 50 % of screen height, and eats taps so sheet content does
 * not bubble up to the scrim.
 */
@Composable
fun AssistOverlay(
    stateFlow: StateFlow<AssistUiState>,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit = {},
    onStopMic: () -> Unit = {},
) {
    val state by stateFlow.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Fixed at ~50% screen height — substantial bottom-sheet
                // presence, content anchors at top of the column.
                .fillMaxHeight(0.5f)
                .pointerInput(Unit) {
                    // Consume taps on the sheet so they do not propagate to
                    // the scrim's dismiss handler.
                    detectTapGestures(onTap = {})
                },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            AssistOverlayBody(
                state = state,
                onOpenApp = onOpenApp,
                onStopMic = onStopMic,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun AssistOverlayBody(
    state: AssistUiState,
    onOpenApp: () -> Unit,
    onStopMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state.phase) {
            Phase.Connecting -> StatusRow(spinner = true, text = "Connecting…")
            Phase.Listening -> {
                StatusRow(
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    text = "Listening",
                )
                Button(onClick = onStopMic) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop")
                }
            }
            Phase.Transcribing -> StatusRow(spinner = true, text = "Hearing you…")
            Phase.Thinking -> {
                if (state.transcript.isNotBlank()) TranscriptBubble(state.transcript)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Thinking…", style = MaterialTheme.typography.bodyLarge)
                    if (state.toolCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("using ${state.toolCount} tools") },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }
            }
            Phase.Replying -> {
                if (state.transcript.isNotBlank()) TranscriptBubble(state.transcript)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Text(
                        text = state.reply.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Phase.NoSpeech -> StatusRow(text = "Didn't catch that")
            Phase.PermissionMissing -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Microphone permission required",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = onOpenApp) { Text("Open HavenCore") }
            }
            Phase.Error -> StatusRow(
                text = state.errorMessage ?: "Something went wrong",
            )
        }
    }
}

@Composable
private fun StatusRow(
    text: String,
    spinner: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            spinner -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            icon != null -> icon()
            else -> Spacer(modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TranscriptBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
