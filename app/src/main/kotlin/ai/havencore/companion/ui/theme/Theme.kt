package ai.havencore.companion.ui.theme

import ai.havencore.companion.data.ThemeMode
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun HavenCoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> HavenDarkColors
        else -> HavenLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HavenTypography,
        shapes = HavenShapes,
        content = content,
    )
}

/**
 * Resolves a [ThemeMode] preference to the boolean `darkTheme` flag
 * that `HavenCoreTheme` consumes. `System` defers to
 * `isSystemInDarkTheme()` so the OS toggle still works.
 */
@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
