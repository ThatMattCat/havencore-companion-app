package ai.havencore.companion.push

import ai.havencore.companion.HavenCoreApp
import android.content.Context
import android.util.Log
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
        // Agent-side registration handoff lands in commit 5 via PushManager.onEndpoint.
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        Log.w("Push:Reg", "registration failed for instance=$instance: $reason")
    }

    override fun onUnregistered(context: Context, instance: String) {
        Log.i("Push:Reg", "unregistered instance=$instance")
    }
}
