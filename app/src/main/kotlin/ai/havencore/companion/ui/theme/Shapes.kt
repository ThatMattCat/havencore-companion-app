package ai.havencore.companion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape

/**
 * Maps the radius scale (`HavenTokens.Radius.*`) onto the five
 * Material 3 shape buckets. Wired into `MaterialTheme(shapes = ...)`
 * by `HavenCoreTheme`. See `docs/design-system.md`.
 */
val HavenShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(HavenTokens.Radius.xs),
    small = RoundedCornerShape(HavenTokens.Radius.sm),
    medium = RoundedCornerShape(HavenTokens.Radius.md),
    large = RoundedCornerShape(HavenTokens.Radius.lg),
    extraLarge = RoundedCornerShape(HavenTokens.Radius.xl),
)

/**
 * Off-scale brand shapes used for visual identity moments. The user
 * bubble's bottom-end nip distinguishes it from the assistant card at
 * a glance — together with the right-alignment + primaryContainer
 * color it forms one of the four brand-glyph shapes (see
 * `docs/design-system.md`).
 */
object HavenBrandShapes {
    val UserBubble: Shape = RoundedCornerShape(
        topStart = HavenTokens.Radius.lg,
        topEnd = HavenTokens.Radius.lg,
        bottomStart = HavenTokens.Radius.lg,
        bottomEnd = HavenTokens.Radius.xs,
    )
}
