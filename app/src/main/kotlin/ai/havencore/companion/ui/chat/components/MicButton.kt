package ai.havencore.companion.ui.chat.components

import ai.havencore.companion.ui.chat.VoiceUi
import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

private const val AMPLITUDE_REFERENCE: Float = 18000f

@Composable
fun MicButton(
    voice: VoiceUi,
    enabled: Boolean,
    amplitudeFlow: StateFlow<Int>,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (voice == VoiceUi.Recording) {
            AmplitudeHalo(amplitudeFlow = amplitudeFlow)
        }
        when (voice) {
            VoiceUi.Idle -> IconButton(onClick = onTap, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Start voice input",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VoiceUi.Recording -> IconButton(onClick = onTap, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop recording",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            VoiceUi.Transcribing -> IconButton(onClick = {}, enabled = false) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            VoiceUi.Speaking -> IconButton(onClick = onTap, enabled = enabled) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Stop playback and start voice input",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Echoes the assist overlay's `MicLevelHero` halo at input-bar scale —
 * a soft primary disc behind the icon that scales and fades with mic
 * amplitude, so the chat input feels alive while recording.
 */
@Composable
private fun AmplitudeHalo(amplitudeFlow: StateFlow<Int>) {
    val amplitude by amplitudeFlow.collectAsState()
    val normalized = (amplitude / AMPLITUDE_REFERENCE).coerceIn(0f, 1f)
    val scale by animateFloatAsState(
        targetValue = 0.85f + normalized * 0.35f,
        animationSpec = tween(durationMillis = HavenTokens.Motion.Instant, easing = LinearEasing),
        label = "mic-halo-scale",
    )
    val alpha = 0.18f + normalized * 0.32f
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                shape = CircleShape,
            ),
    )
}

