package ai.havencore.companion.push

import ai.havencore.companion.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object PushChannel {
    const val ID = "havencore_autonomy"

    fun register(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(ID) != null) return
        val ch = NotificationChannel(
            ID,
            ctx.getString(R.string.notif_channel_name_autonomy),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = ctx.getString(R.string.notif_channel_desc_autonomy)
            enableVibration(true)
            setShowBadge(true)
        }
        mgr.createNotificationChannel(ch)
    }
}
