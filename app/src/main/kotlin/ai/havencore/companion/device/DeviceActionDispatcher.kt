package ai.havencore.companion.device

import ai.havencore.companion.data.DeviceAction
import ai.havencore.companion.data.DeviceActionResult
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

/**
 * Fires Android Intents on behalf of the agent. Lives in the
 * AppContainer; both ChatViewModel and HavenAssistSession dispatch
 * through the same instance so chat-screen and assist-overlay flows
 * share the same intent path.
 *
 * Intents originate from a non-Activity Context, so every Intent
 * must carry FLAG_ACTIVITY_NEW_TASK.
 */
class DeviceActionDispatcher(private val context: Context) {

    fun dispatch(action: DeviceAction): DeviceActionResult = try {
        when (action) {
            is DeviceAction.SetAlarm -> setAlarm(action)
        }
    } catch (t: ActivityNotFoundException) {
        Log.w(TAG, "no activity for action=$action", t)
        DeviceActionResult.NoHandler
    } catch (t: Throwable) {
        Log.w(TAG, "device action failed: $action", t)
        DeviceActionResult.Failed(t.message ?: t.javaClass.simpleName)
    }

    private fun setAlarm(action: DeviceAction.SetAlarm): DeviceActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, action.skipUi)
            putExtra(AlarmClock.EXTRA_VIBRATE, action.vibrate)
            action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            if (action.daysOfWeek.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(action.daysOfWeek))
            }
        }
        context.startActivity(intent)
        Log.i(TAG, "set_alarm fired hour=${action.hour} minute=${action.minute}")
        return DeviceActionResult.Fired
    }

    private companion object {
        const val TAG = "DeviceAction"
    }
}
