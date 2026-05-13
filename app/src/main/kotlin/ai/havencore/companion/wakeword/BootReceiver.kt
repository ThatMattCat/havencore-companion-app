package ai.havencore.companion.wakeword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores the wall-display foreground mic service after a reboot. Without
 * this, `HavenCoreApp.onCreate` would only fire on the next user app-launch,
 * meaning a power blip silently kills wake-word until someone touches the
 * tablet. Uses [goAsync] because the autostart check reads DataStore and
 * the receiver instance is destroyed once `onReceive` returns.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            try {
                WakeWordAutostart.maybeStart(context)
            } finally {
                pending.finish()
            }
        }
    }
}
