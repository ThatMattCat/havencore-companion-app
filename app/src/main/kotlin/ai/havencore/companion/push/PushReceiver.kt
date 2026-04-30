package ai.havencore.companion.push

import ai.havencore.companion.HavenCoreApp
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class PushReceiver : MessagingReceiver() {

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        val preview = message.content.toString(Charsets.UTF_8).take(120)
        Log.i("Push:Recv", "received ${message.content.size}B for instance=$instance: $preview")
        val app = (context.applicationContext as HavenCoreApp).container
        val parsed = parsePushPayload(message.content).getOrElse { e ->
            Log.w("Push:Recv", "malformed push payload: ${e.message}")
            return
        }
        app.pushNotifier.notify(parsed)
    }

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        Log.i("Push:Reg", "new endpoint for instance=$instance: ${endpoint.url}")
        val app = (context.applicationContext as HavenCoreApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.pushManager.onEndpoint(endpoint.url)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        Log.w("Push:Reg", "registration failed for instance=$instance: $reason")
        val app = (context.applicationContext as HavenCoreApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.pushManager.onRegistrationFailed(reason)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUnregistered(context: Context, instance: String) {
        Log.i("Push:Reg", "unregistered instance=$instance")
        val app = (context.applicationContext as HavenCoreApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.pushManager.onUnregistered()
            } finally {
                pending.finish()
            }
        }
    }
}
