package ai.havencore.companion.wakeword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Debug-only entry point that lets adb start the overlay without a real
 * wake-word fire. Receiver is exported because adb broadcasts can't
 * target non-exported components, but it simply forwards to the
 * (non-exported) [AvatarOverlayService] using the DEBUG_SHOW action.
 *
 * Trigger:
 * ```
 * adb shell am broadcast -a ai.havencore.companion.avatar.DEBUG_SHOW
 * ```
 *
 * The overlay auto-dismisses on the normal idle-timeout path.
 */
class AvatarOverlayDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "DEBUG_SHOW broadcast received; starting overlay service")
        val svc = Intent(context, AvatarOverlayService::class.java)
            .setAction(AvatarOverlayService.ACTION_DEBUG_SHOW)
        runCatching {
            ContextCompat.startForegroundService(context, svc)
        }.onFailure { Log.w(TAG, "failed to start AvatarOverlayService", it) }
    }

    private companion object {
        const val TAG = "Avatar:DbgRx"
    }
}
