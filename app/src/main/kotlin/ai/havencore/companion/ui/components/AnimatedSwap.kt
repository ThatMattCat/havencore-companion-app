package ai.havencore.companion.ui.components

import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Phase-swap recipe — fade-and-rise on enter, fade on exit, container
 * size animates between phases. The assist overlay's `PhaseBody` is
 * the canonical instance; reuse here for any state-machine transition
 * (history loading → loaded → empty / error, settings ping result,
 * etc).
 *
 * `targetState` is whatever you want to swap on — typically a sealed
 * class or enum. `contentKey` (default: identity) controls when a
 * swap actually fires; pass a stable key (e.g. `{ it::class }`) when
 * different instances of the same subclass should not re-animate.
 *
 * See `docs/design-system.md`.
 */
@Composable
fun <T> AnimatedSwap(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    label: String = "swap",
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(HavenTokens.Motion.Standard)) +
                slideInVertically(tween(HavenTokens.Motion.Standard)) { it / 6 })
                .togetherWith(fadeOut(tween(HavenTokens.Motion.Fast)))
                .using(SizeTransform(clip = false))
        },
        contentKey = contentKey,
        label = label,
    ) { state ->
        content(state)
    }
}
