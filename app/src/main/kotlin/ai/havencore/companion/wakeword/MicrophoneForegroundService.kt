package ai.havencore.companion.wakeword

import ai.havencore.companion.HavenCoreApp
import ai.havencore.companion.MainActivity
import ai.havencore.companion.R
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground microphone service for wall-display mode. Holds the mic
 * continuously via [WakeWordController]; on detection it captures one
 * utterance (Silero-endpointed) and launches [MainActivity] in kiosk mode,
 * passing the captured WAV path so the chat path can transcribe + send
 * without the user needing to tap PTT.
 *
 * Started by HavenCoreApp on cold-boot when wall_display_enabled is true,
 * and by the Settings toggle when the user flips it on.
 *
 * Stops itself if the WakeWordController emits a fatal Failed event for the
 * engine_start stage (typically missing ONNX assets) — the sticky
 * notification is removed and the user can re-enable from Settings after
 * fixing the asset.
 */
class MicrophoneForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var controller: WakeWordController? = null
    private var eventJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WakeWordChannel.register(this)
        startForegroundCompat()
        scope.launch { bootController() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private suspend fun bootController() {
        val app = applicationContext as HavenCoreApp
        val settings = app.container.settings
        val modelAsset = settings.wakeWordModelAssetFlow.first()
        val threshold = settings.wakeWordThresholdFlow.first()
        val captureDir = File(applicationContext.cacheDir, "wake-captures")
        val cfg = WakeWordController.Config(
            modelAssetPath = modelAsset,
            threshold = threshold,
            captureOutDir = captureDir,
        )
        val c = WakeWordController(applicationContext, cfg)
        controller = c
        eventJob = scope.launch {
            c.events.collect { ev -> handleEvent(ev) }
        }
        c.start()
    }

    private fun handleEvent(ev: WakeWordController.Event) {
        when (ev) {
            is WakeWordController.Event.ListeningStarted -> {
                Log.i(TAG, "listening")
            }
            is WakeWordController.Event.Detected -> {
                Log.i(TAG, "detected ${ev.modelName} score=${ev.score}")
            }
            is WakeWordController.Event.Captured -> {
                Log.i(TAG, "captured ${ev.file.name} durMs=${ev.durationMs} speech=${ev.hadSpeech}")
                if (ev.hadSpeech) launchChatWithCapture(ev.file)
            }
            is WakeWordController.Event.Failed -> {
                Log.w(TAG, "stage=${ev.stage} cause=${ev.cause.message}")
                if (ev.stage == "engine_start") stopSelf()
            }
            WakeWordController.Event.Stopped -> {
                Log.i(TAG, "controller stopped")
            }
        }
    }

    private fun launchChatWithCapture(file: File) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_KIOSK, true)
            putExtra(EXTRA_CAPTURE_PATH, file.absolutePath)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "startActivity failed", it) }
    }

    private fun startForegroundCompat() {
        val stopIntent = Intent(this, MicrophoneForegroundService::class.java)
            .setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val contentPi = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, WakeWordChannel.ID)
            .setSmallIcon(R.drawable.ic_push_small)
            .setContentTitle(getString(R.string.wakeword_notif_title))
            .setContentText(getString(R.string.wakeword_notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPi)
            .addAction(0, getString(R.string.wakeword_notif_stop), stopPi)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun onDestroy() {
        eventJob?.cancel()
        controller?.stop()
        controller = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WakeWord:Svc"
        private const val NOTIF_ID = 0x57414b45 // "WAKE"

        const val ACTION_STOP = "ai.havencore.companion.wakeword.STOP"
        const val EXTRA_KIOSK = "kiosk_mode"
        const val EXTRA_CAPTURE_PATH = "wake_capture_path"
    }
}
