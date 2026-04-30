package ai.havencore.companion.push

import ai.havencore.companion.MainActivity
import ai.havencore.companion.R
import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class PushNotifier(private val ctx: Context) {

    fun notify(payload: PushPayload) {
        if (!hasPostNotificationsPermission()) {
            Log.w("Push:Recv", "POST_NOTIFICATIONS not granted; dropping ${payload.type}")
            return
        }
        val tap = buildTapIntent(payload.sessionId)
        val (priority, vibration) = mapSeverity(payload.severity)
        val notif = NotificationCompat.Builder(ctx, PushChannel.ID)
            .setSmallIcon(R.drawable.ic_push_small)
            .setContentTitle(payload.title.ifBlank { "Selene" })
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .also { b -> if (vibration != null) b.setVibrate(vibration) }
            .build()
        NotificationManagerCompat.from(ctx).notify(notificationId(), notif)
    }

    private fun mapSeverity(s: String): Pair<Int, LongArray?> = when (s) {
        "alert" -> NotificationCompat.PRIORITY_HIGH to longArrayOf(0, 300, 150, 300, 150, 300)
        "warn" -> NotificationCompat.PRIORITY_HIGH to longArrayOf(0, 250, 150, 250)
        else -> NotificationCompat.PRIORITY_DEFAULT to null
    }

    private fun buildTapIntent(sessionId: String?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        val rc = sessionId?.hashCode() ?: 0
        return PendingIntent.getActivity(
            ctx, rc, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun notificationId(): Int =
        (System.currentTimeMillis() and 0x7fffffff).toInt()

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
    }
}
