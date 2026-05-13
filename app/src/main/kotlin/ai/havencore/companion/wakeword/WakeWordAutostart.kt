package ai.havencore.companion.wakeword

import ai.havencore.companion.HavenCoreApp
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Shared entry point that starts [MicrophoneForegroundService] iff the
 * wall-display toggle is on. Called from [HavenCoreApp.onCreate] (warm
 * starts) and [BootReceiver.onReceive] (cold boot). Suspending because
 * it reads a DataStore-backed setting.
 */
object WakeWordAutostart {

    private const val TAG = "WakeWordAutostart"

    suspend fun maybeStart(context: Context) {
        val app = context.applicationContext as HavenCoreApp
        val enabled = app.container.settings.wallDisplayEnabled()
        if (!enabled) return
        val intent = Intent(context, MicrophoneForegroundService::class.java)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { Log.w(TAG, "wake-word service autostart failed", it) }
    }
}
