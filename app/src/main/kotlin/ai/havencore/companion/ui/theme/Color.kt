package ai.havencore.companion.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand seed and full Material 3 color schemes. See
 * `docs/design-system.md`.
 *
 * Both schemes define every role explicitly. Regenerate via the
 * Material 3 theme builder (https://material-foundation.github.io/material-theme-builder/)
 * seeded with `BrandSeed` if a deeper retune is needed; do not
 * hand-edit individual roles in isolation — drift will follow.
 *
 * Dynamic color (Android 12+) overrides these on-device by default;
 * these are the fallback for older devices and for a future
 * "brand-locked" Settings toggle.
 */
val BrandSeed: Color = Color(0xFF6E8AAB)

val HavenLightColors = lightColorScheme(
    primary = Color(0xFF355F8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    inversePrimary = Color(0xFFA2C9FF),

    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF101C2B),

    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceTint = Color(0xFF355F8A),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF0F0F4),

    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),

    scrim = Color(0xFF000000),
)

val HavenDarkColors = darkColorScheme(
    primary = Color(0xFFA2C9FF),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF194A7E),
    onPrimaryContainer = Color(0xFFD2E4FF),
    inversePrimary = Color(0xFF355F8A),

    secondary = Color(0xFFBBC7DC),
    onSecondary = Color(0xFF253141),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F8),

    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF524060),
    onTertiaryContainer = Color(0xFFF2DAFF),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Anchored to the original HavenBackground / HavenSurface tones
    // so the dark theme keeps the familiar slate look.
    background = Color(0xFF111827),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    surfaceTint = Color(0xFFA2C9FF),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2F3033),

    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),

    scrim = Color(0xFF000000),
)
