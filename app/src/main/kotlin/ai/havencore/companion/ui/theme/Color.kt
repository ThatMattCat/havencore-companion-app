package ai.havencore.companion.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Quiet Tech palette. Slate canvas, cool blue primary, warm amber as
 * the rare accent role. See `docs/design-system.md` for the full
 * identity rationale and role usage rules.
 *
 * Both schemes define every Material 3 role explicitly. To re-skin,
 * regenerate from the [theme builder](https://material-foundation.github.io/material-theme-builder/)
 * seeded with `BrandSeed` and paste the output here. Do not hand-edit
 * individual roles in isolation.
 *
 * Dynamic color (Android 12+) is OFF by default so the brand identity
 * is the canonical experience. Users can opt in via Settings.
 */
val BrandSeed: Color = Color(0xFF2D5BA1)

val HavenLightColors = lightColorScheme(
    primary = Color(0xFF2D5BA1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    inversePrimary = Color(0xFFADCBF8),

    secondary = Color(0xFF545F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),

    // Tertiary is the rare "alive" accent. Reserved for moments
    // worth noticing (assist Replying, cache-hit chip). Warm
    // ochre/amber so it pops against the cool palette.
    tertiary = Color(0xFF8A6420),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB1),
    onTertiaryContainer = Color(0xFF2C1700),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    surfaceTint = Color(0xFF2D5BA1),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),

    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),

    scrim = Color(0xFF000000),
)

val HavenDarkColors = darkColorScheme(
    primary = Color(0xFFADCBF8),
    onPrimary = Color(0xFF002F5F),
    primaryContainer = Color(0xFF194878),
    onPrimaryContainer = Color(0xFFD5E3FF),
    inversePrimary = Color(0xFF2D5BA1),

    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4858),
    onSecondaryContainer = Color(0xFFD8E3F8),

    // Warm amber accent. The single point of warmth in the dark
    // scheme — used sparingly so it keeps meaning.
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF492A00),
    tertiaryContainer = Color(0xFF693E00),
    onTertiaryContainer = Color(0xFFFFDDB1),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Deep slate canvas. Slightly deeper than the previous
    // #111827 so cards (#1A2332) have clear separation without
    // a stroke.
    background = Color(0xFF0F1622),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A2332),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6CF),
    surfaceTint = Color(0xFFADCBF8),
    inverseSurface = Color(0xFFE2E2E6),
    inverseOnSurface = Color(0xFF2F3033),

    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),

    scrim = Color(0xFF000000),
)
