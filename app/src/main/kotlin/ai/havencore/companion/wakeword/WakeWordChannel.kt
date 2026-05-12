package ai.havencore.companion.wakeword

import ai.havencore.companion.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object WakeWordChannel {
    const val ID = "havencore_wakeword"

    fun register(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(ID) != null) return
        val ch = NotificationChannel(
            ID,
            ctx.getString(R.string.notif_channel_name_wakeword),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = ctx.getString(R.string.notif_channel_desc_wakeword)
            setShowBadge(false)
            enableVibration(false)
        }
        mgr.createNotificationChannel(ch)
    }
}
