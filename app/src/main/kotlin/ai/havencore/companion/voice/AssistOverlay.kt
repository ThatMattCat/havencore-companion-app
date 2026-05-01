package ai.havencore.companion.voice

import ai.havencore.companion.audio.TtsPlayer
import ai.havencore.companion.ui.components.HeroDisc
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/**
 * Bottom-sheet assist overlay. The outer Box paints a 40 % black scrim
 * across the whole window (the session Window is sized MATCH_PARENT) and
 * dismisses on any tap. The inner Surface is anchored at the bottom,
 * grows / shrinks with content (capped at 72 % of screen height), and
 * eats taps so sheet content does not bubble up to the scrim.
 */
@Composable
fun AssistOverlay(
    stateFlow: StateFlow<AssistUiState>,
    amplitudeFlow: StateFlow<Int>,
    ttsStateFlow: StateFlow<TtsPlayer.State>,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit = {},
    onStopMic: () -> Unit = {},
) {
    val state by stateFlow.collectAsState()
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.72f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Sheet-specific minimum so the body doesn't collapse during
                // brief phase transitions.
                .heightIn(min = 240.dp, max = maxSheetHeight)
                .animateContentSize(animationSpec = tween(HavenTokens.Motion.Slow))
                .pointerInput(Unit) {
                    // Consume taps on the sheet so they do not propagate to
                    // the scrim's dismiss handler.
                    detectTapGestures(onTap = {})
                },
            shape = HavenTokens.Radius.sheetTop,
            tonalElevation = HavenTokens.Elevation.Level2,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = HavenTokens.Spacing.xl,
                        vertical = HavenTokens.Spacing.md,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DragHandle()
                Spacer(modifier = Modifier.size(HavenTokens.Spacing.sm))
                HeaderRow(state.phase)
                Spacer(modifier = Modifier.size(HavenTokens.Spacing.xl))
                PhaseBody(
                    state = state,
                    amplitudeFlow = amplitudeFlow,
                    ttsStateFlow = ttsStateFlow,
                    onOpenApp = onOpenApp,
                    onStopMic = onStopMic,
                    onDismiss = onDismiss,
                )
                Spacer(modifier = Modifier.size(HavenTokens.Spacing.xl))
            }
        }
    }
}

@Composable
private fun DragHandle() {
    // Drag handle dimensions are pixel-precise visual constants; standard
    // Material 3 sheet handle is 32×4 with a 2.dp corner.
    Box(
        modifier = Modifier
            .padding(top = HavenTokens.Spacing.xs)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun HeaderRow(phase: Phase) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "HavenCore",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StatusPill(phase)
    }
}

@Composable
private fun StatusPill(phase: Phase) {
    val (label, container, content) = when (phase) {
        Phase.Connecting -> Triple("Connecting", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        Phase.Listening -> Triple("Listening", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        Phase.Transcribing -> Triple("Hearing you", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        Phase.Thinking -> Triple("Thinking", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        Phase.Replying -> Triple("Speaking", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        Phase.NoSpeech -> Triple("No speech", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        Phase.PermissionMissing -> Triple("Permission needed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        Phase.Error -> Triple("Error", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(
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

@Composable
private fun PhaseBody(
    state: AssistUiState,
    amplitudeFlow: StateFlow<Int>,
    ttsStateFlow: StateFlow<TtsPlayer.State>,
    onOpenApp: () -> Unit,
    onStopMic: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedContent(
        targetState = state.phase,
        transitionSpec = {
            (fadeIn(tween(HavenTokens.Motion.Standard)) +
                slideInVertically(tween(HavenTokens.Motion.Standard)) { it / 6 }) togetherWith
                fadeOut(tween(HavenTokens.Motion.Fast)) using SizeTransform(clip = false)
        },
        contentKey = { it::class },
        label = "phase-body",
    ) { phase ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HavenTokens.Spacing.lg),
        ) {
            when (phase) {
                Phase.Connecting -> ConnectingRing()

                Phase.Listening -> {
                    MicLevelHero(amplitudeFlow)
                    MicLevelEqualizer(amplitudeFlow)
                    Spacer(modifier = Modifier.size(HavenTokens.Spacing.xs))
                    StopButton(onStopMic)
                }

                Phase.Transcribing -> {
                    ConnectingRing()
                    Text(
                        text = "Hearing you…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Phase.Thinking -> {
                    ThinkingDots()
                    if (state.transcript.isNotBlank()) TranscriptBubble(state.transcript)
                    if (state.toolCount > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("using ${state.toolCount} tools") },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }

                Phase.Replying -> {
                    val ttsState by ttsStateFlow.collectAsState()
                    val active = ttsState is TtsPlayer.State.Loading ||
                        ttsState is TtsPlayer.State.Playing
                    SpeakingBars(active = active)
                    if (state.transcript.isNotBlank()) TranscriptBubble(state.transcript)
                    if (state.reply.isNotBlank()) ReplyBubble(state.reply)
                }

                Phase.NoSpeech -> {
                    HeroDisc(
                        icon = Icons.Filled.MicOff,
                        discColor = MaterialTheme.colorScheme.surfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Didn't catch that",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }

                Phase.PermissionMissing -> {
                    HeroDisc(
                        icon = Icons.Filled.Mic,
                        discColor = MaterialTheme.colorScheme.errorContainer,
                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "Microphone permission required",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(onClick = onOpenApp) { Text("Open HavenCore") }
                }

                Phase.Error -> {
                    HeroDisc(
                        icon = Icons.Filled.ErrorOutline,
                        discColor = MaterialTheme.colorScheme.errorContainer,
                        iconColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = state.errorMessage ?: "Something went wrong",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = "Stop listening",
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun TranscriptBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.75f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(
                    horizontal = HavenTokens.Spacing.md,
                    vertical = HavenTokens.Spacing.sm,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ReplyBubble(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = HavenTokens.Spacing.lg,
                vertical = HavenTokens.Spacing.md,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

