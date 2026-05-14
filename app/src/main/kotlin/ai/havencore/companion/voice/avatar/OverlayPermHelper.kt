package ai.havencore.companion.voice.avatar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Tracks whether the user has granted SYSTEM_ALERT_WINDOW so AvatarOverlayService
 * can draw a window on top of HA (or any other foreground app). Without this
 * grant, the overlay window create call silently no-ops on most OEM builds.
 *
 * Shape mirrors [ai.havencore.companion.wakeword.BatteryOptHelper] — same
 * read-state + intent-emit interface so the Settings card row can use the
 * same composable shell.
 */
object OverlayPermHelper {

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun requestIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }
}
