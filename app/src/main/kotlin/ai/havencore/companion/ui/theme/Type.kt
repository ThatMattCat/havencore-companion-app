package ai.havencore.companion.ui.theme

import androidx.compose.material3.Typography

/**
 * Stock Material 3 Typography — system sans across the M3 type scale.
 *
 * The Quiet Tech identity calls for Inter (see `docs/design-system.md`),
 * but adopting it cleanly requires shipping a `res/values/font_certs.xml`
 * with the Google Fonts provider certs (a long, opaque base64 file
 * neither AndroidX nor Compose ship for us). Deferred until that
 * resource lands so this file stays scrutable. To enable Inter once
 * the certs file exists, build a `FontFamily` via
 * `androidx.compose:ui-text-google-fonts` and pass it through
 * `Typography(...)` here. One file, no screen edits.
 */
val HavenTypography: Typography = Typography()
