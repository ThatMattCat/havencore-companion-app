package ai.havencore.companion.ui.components

import ai.havencore.companion.ui.theme.HavenTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Static icon-on-disc hero. Sized to `HavenTokens.Hero.Disc` so
 * adjacent layouts (sheets, empty states) keep stable height; the
 * filled disc sits inside at `Hero.InnerDisc` with a `Hero.Icon`
 * glyph.
 *
 * Use for screen-level empty / error / explanatory surfaces and for
 * the assist overlay's terminal phases. In-line errors (banners,
 * field validation) do not use a hero. See `docs/design-system.md`.
 */
@Composable
fun HeroDisc(
    icon: ImageVector,
    discColor: Color,
    iconColor: Color,
    contentDescription: String? = null,
) {
    Box(
        modifier = Modifier.size(HavenTokens.Hero.Disc),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(HavenTokens.Hero.InnerDisc)
                .clip(CircleShape)
                .background(discColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(HavenTokens.Hero.Icon),
                tint = iconColor,
            )
        }
    }
}

/**
 * Inline-sized variant of `HeroDisc` for leading positions in cards
 * and list rows — same circle-with-icon pattern, just smaller and
 * without the outer halo box. Default size matches a comfortable
 * card-leading visual weight (32 dp disc, 18 dp icon).
 */
@Composable
fun AccentDisc(
    icon: ImageVector,
    discColor: Color,
    iconColor: Color,
    contentDescription: String? = null,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(discColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = iconColor,
        )
    }
}
