package ai.havencore.companion.voice

import ai.havencore.companion.audio.MicRecorder
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

internal val HeroSize: Dp = 120.dp

private const val AMPLITUDE_HEADROOM = 6800f

private fun normalizeAmplitude(amp: Int): Float {
    val above = (amp - MicRecorder.MIN_PEAK_AMPLITUDE).coerceAtLeast(0).toFloat()
    return (above / AMPLITUDE_HEADROOM).coerceIn(0f, 1f)
}

@Composable
internal fun ConnectingRing() {
    Box(
        modifier = Modifier.size(HeroSize),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            strokeWidth = 4.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * Live mic-level visualizer. Outer halo scales with the *current* amplitude
 * reading; inner Mic-on-disc stays put. Reads the same StateFlow the
 * silence watcher consumes, so when the watcher decides to auto-stop the
 * UI is showing the same signal that triggered it.
 */
@Composable
internal fun MicLevelHero(amplitudeFlow: StateFlow<Int>) {
    val amplitude by amplitudeFlow.collectAsState()
    val normalized = normalizeAmplitude(amplitude)
    val scale by animateFloatAsState(
        targetValue = 1.0f + normalized * 0.35f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "mic-pulse-scale",
    )
    val haloAlpha by animateFloatAsState(
        targetValue = 0.18f + normalized * 0.45f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "mic-halo-alpha",
    )

    Box(
        modifier = Modifier.size(HeroSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(HeroSize)
                .scale(scale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha)),
        )
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * 5-bar equalizer fed by a ring buffer of recent amplitude samples. Sits
 * below the hero during Listening to give a sense of audio history.
 */
@Composable
internal fun MicLevelEqualizer(amplitudeFlow: StateFlow<Int>) {
    val history = remember { mutableStateListOf(0, 0, 0, 0, 0) }
    LaunchedEffect(amplitudeFlow) {
        // Sampled at 150ms — slower than the recorder's 100ms poll so the
        // bars have a visible cadence rather than blurring.
        while (true) {
            for (i in history.lastIndex downTo 1) history[i] = history[i - 1]
            history[0] = amplitudeFlow.value
            delay(150)
        }
    }

    Row(
        modifier = Modifier.height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        history.forEach { amp ->
            val target = 0.2f + normalizeAmplitude(amp) * 0.8f
            val height by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 180),
                label = "eq-bar-height",
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * Three dots, staggered scale-pulse. Used during Thinking.
 */
@Composable
internal fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking-dots")
    Box(
        modifier = Modifier.size(HeroSize),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) { index ->
                val scale by transition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 600,
                            delayMillis = index * 200,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot-scale-$index",
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * 5 vertical bars cycling on an infinite transition. Each bar has its own
 * period and phase offset so the group looks organic rather than locked.
 * `active` gates the animation — when false, bars rest at minimum height.
 */
@Composable
internal fun SpeakingBars(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "speaking-bars")
    val periods = listOf(520, 680, 460, 740, 580)
    val phases = listOf(0, 180, 90, 250, 60)
    Box(
        modifier = Modifier.size(HeroSize),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(72.dp),
        ) {
            periods.forEachIndexed { i, period ->
                val frac by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = period,
                            delayMillis = phases[i],
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar-$i",
                )
                val target = if (active) frac else 0.2f
                val animated by animateFloatAsState(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 180),
                    label = "bar-target-$i",
                )
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .fillMaxHeight(animated)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * Non-animated icon-on-disc hero used for terminal phases (NoSpeech,
 * PermissionMissing, Error).
 */
@Composable
internal fun StaticHeroIcon(
    icon: ImageVector,
    discColor: Color,
    iconColor: Color,
) {
    Box(
        modifier = Modifier.size(HeroSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(discColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = iconColor,
            )
        }
    }
}
