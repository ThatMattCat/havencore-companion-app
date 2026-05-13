package ai.havencore.companion.wakeword

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Tracks whether the user has whitelisted the app from Doze / adaptive
 * battery. Without the exemption the wall-display mic service gets
 * throttled / suspended after a few hours idle (worse on OEM skins).
 *
 * Sideloaded build, so it's fine to use [ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]
 * directly for the one-tap allow dialog — Play Store policy that bans this
 * intent for non-essential apps doesn't apply.
 */
object BatteryOptHelper {

    fun isIgnoring(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }
}
