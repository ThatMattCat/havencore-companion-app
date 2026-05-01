package ai.havencore.companion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

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
