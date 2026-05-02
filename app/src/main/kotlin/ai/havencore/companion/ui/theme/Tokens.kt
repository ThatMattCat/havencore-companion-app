package ai.havencore.companion.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Non-Material design tokens. See `docs/design-system.md`.
 *
 * Color, typography, and shape live in `MaterialTheme.colorScheme`,
 * `MaterialTheme.typography`, and `MaterialTheme.shapes`. Everything
 * else (spacing, motion, semantic elevation, hero sizes) lives here.
 */
object HavenTokens {

    object Spacing {
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 20.dp
        val xxl: Dp = 32.dp
    }

    object Radius {
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 20.dp
        val xl: Dp = 28.dp

        val pill: Shape = RoundedCornerShape(50)
        val sheetTop: Shape = RoundedCornerShape(topStart = xl, topEnd = xl)
    }

    object Motion {
        const val Instant: Int = 100
        const val Fast: Int = 150
        const val Standard: Int = 200
        const val Slow: Int = 220

        val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
        val Standard1: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    }

    object Elevation {
        val Level0: Dp = 0.dp
        val Level1: Dp = 2.dp
        val Level2: Dp = 4.dp
        val Level3: Dp = 8.dp
    }

    object Hero {
        val Disc: Dp = 120.dp
        val InnerDisc: Dp = 76.dp
        val Icon: Dp = 36.dp
    }
}
