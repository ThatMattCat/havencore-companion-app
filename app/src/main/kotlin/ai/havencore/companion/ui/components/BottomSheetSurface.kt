package ai.havencore.companion.ui.components

import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The assist overlay's outer Surface anatomy, packaged so future
 * sheets share its proportions: top-only large radius, Level2 tonal
 * elevation, content-size animation on the slow track, drag handle.
 *
 * This component is the *visible sheet* — it does NOT manage the
 * scrim, dismiss gestures, or sizing relative to the window. Wrap it
 * in your own `Box` if you need those (the assist overlay does).
 *
 * `minHeight` defaults to 240 dp so brief phase transitions don't
 * collapse the sheet visibly. `maxHeight` is opt-in.
 *
 * See `docs/design-system.md`.
 */
@Composable
fun BottomSheetSurface(
    modifier: Modifier = Modifier,
    minHeight: Dp = 240.dp,
    maxHeight: Dp = Dp.Infinity,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight, max = maxHeight)
            .animateContentSize(animationSpec = tween(HavenTokens.Motion.Slow)),
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
            content()
        }
    }
}

/**
 * Standard Material 3 sheet drag handle (32×4 dp pill, outlineVariant
 * tint). Public so non-`BottomSheetSurface` sheets can reuse the same
 * affordance.
 */
@Composable
fun DragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = HavenTokens.Spacing.xs)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
