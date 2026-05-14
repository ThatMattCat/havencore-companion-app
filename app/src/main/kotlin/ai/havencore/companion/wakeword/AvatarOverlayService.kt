package ai.havencore.companion.wakeword

import ai.havencore.companion.HavenCoreApp
import ai.havencore.companion.R
import ai.havencore.companion.audio.TtsPlayer
import ai.havencore.companion.voice.AssistLifecycleOwner
import ai.havencore.companion.voice.avatar.AvatarBridge
import ai.havencore.companion.voice.avatar.AvatarController
import ai.havencore.companion.voice.avatar.AvatarPhase
import ai.havencore.companion.voice.avatar.AvatarUiState
import ai.havencore.companion.voice.avatar.OverlayPermHelper
import ai.havencore.companion.voice.avatar.VoiceTurnRunner
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
// LAYER_TYPE_HARDWARE constant lives on View.
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Floating-window host for the Live2D avatar. On wake the
 * [MicrophoneForegroundService] starts this service (passing the captured
 * WAV via [EXTRA_CAPTURE_PATH]); we mount a transparent-background WebView
 * via [WindowManager.addView] using
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] so HA stays
 * visible behind the avatar.
 *
 * Lifecycle:
 *  - onCreate mounts the overlay window and constructs [AvatarController]
 *    + [VoiceTurnRunner] from the singletons in AppContainer.
 *  - onStartCommand routes by intent action — DEBUG_SHOW for manual
 *    smoke-tests, ACTION_STOP from the notification, or the default wake
 *    path which kicks off [VoiceTurnRunner.run] with the capture file.
 *  - The [IdleWatcher] coroutine self-stops the service ~N seconds after
 *    the last user-facing activity (configurable via
 *    [SettingsRepository.avatarIdleTimeoutMsFlow]).
 *
 * Manual debug trigger:
 * ```
 * adb shell am startservice -n ai.havencore.companion/.wakeword.AvatarOverlayService \
 *     -a ai.havencore.companion.avatar.DEBUG_SHOW
 * ```
 */
class AvatarOverlayService : Service() {

    private val container by lazy { (application as HavenCoreApp).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayView: View? = null
    private var webView: WebView? = null
    private lateinit var lifecycleOwner: AssistLifecycleOwner
    private lateinit var controller: AvatarController

    private var idleWatcherJob: Job? = null
    private var stateBindJob: Job? = null
    private var jsReady: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WakeWordChannel.register(this)
        startForegroundCompat()

        if (!OverlayPermHelper.canDrawOverlays(this)) {
            Log.w(
                TAG,
                "SYSTEM_ALERT_WINDOW not granted — overlay won't show; " +
                    "user must grant 'Display over other apps' from Settings.",
            )
            stopSelf()
            return
        }

        lifecycleOwner = AssistLifecycleOwner().also { it.onCreate() }
        controller = AvatarController(container.visemeScheduler, container.ttsPlayer)
        controller.start()

        mountOverlay()
        bindControllerToJs()
        startIdleWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DEBUG_SHOW -> {
                Log.i(TAG, "debug-show: overlay will hold until idle timeout")
                controller.setPhase(AvatarPhase.Listening)
                controller.setCaption("debug overlay")
            }
            else -> {
                val capturePath = intent?.getStringExtra(EXTRA_CAPTURE_PATH)
                if (capturePath != null) {
                    runTurn(File(capturePath))
                } else {
                    Log.w(TAG, "started with no capture path and no recognised action")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun runTurn(captureFile: File) {
        if (!captureFile.exists()) {
            Log.w(TAG, "capture file missing: ${captureFile.absolutePath}")
            controller.setError("Couldn't find capture audio.")
            return
        }
        scope.launch {
            try {
                val runner = VoiceTurnRunner(
                    container = container,
                    controller = controller,
                    visemeScheduler = container.visemeScheduler,
                )
                runner.run(captureFile)
            } catch (t: Throwable) {
                Log.w(TAG, "voice turn failed", t)
                controller.setError(t.message?.take(160) ?: "Turn failed.")
            }
        }
    }

    private fun mountOverlay() {
        val wm = getSystemService<WindowManager>() ?: run {
            Log.e(TAG, "WindowManager unavailable; cannot mount overlay")
            stopSelf()
            return
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = ::buildWebView,
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_HARDWARE_ACCELERATED is required for the WebView's WebGL
            // canvas to composite onto this overlay. Activity windows get
            // it from the manifest's android:hardwareAccelerated="true",
            // but TYPE_APPLICATION_OVERLAY windows must opt in explicitly —
            // without it, HTML/Compose still render but the WebGL surface
            // never makes it to the screen.
            //
            // FLAG_NOT_TOUCHABLE makes every pixel of the overlay pass touches
            // through to HA underneath, which is what we want — the avatar is
            // decoration, not an interactive surface. (Tap-to-open-chat is
            // explicitly out of scope per the plan.) Without this flag the
            // full-screen ComposeView swallows every touch even though it's
            // visually transparent.
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            wm.addView(composeView, params)
            overlayView = composeView
            lifecycleOwner.onStart()
            lifecycleOwner.onResume()
            Log.i(TAG, "overlay mounted")
        } catch (t: Throwable) {
            Log.e(TAG, "addView failed", t)
            stopSelf()
        }
    }

    private fun buildWebView(ctx: Context): WebView {
        val bridge = AvatarBridge(
            onReady = {
                Log.i(TAG, "avatar JS ready")
                jsReady = true
                // Push current state immediately so a freshly-loaded model
                // catches up to whatever phase the controller is already in.
                pushStateToJs(controller.state.value)
            },
            onError = { msg -> Log.w(TAG, "avatar JS error: $msg") },
            onTap = { Log.i(TAG, "avatar tapped") },
        )
        return WebView(ctx).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // file:///android_asset/ uses the file:// scheme. With strict
                // settings the WebView blocks both the top-level load and any
                // cross-origin reads its JS does (Live2D model + textures load
                // via fetch/XHR from relative paths). The overlay never loads
                // anything but bundled assets, so loosening these is safe.
                allowFileAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            setBackgroundColor(Color.TRANSPARENT)
            // Force a hardware-backed layer so WebGL composites correctly
            // onto the overlay window. Without this the WebView's WebGL
            // surface can end up at reduced apparent alpha when blended
            // against a TYPE_APPLICATION_OVERLAY parent window.
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val level = message.messageLevel().name
                    Log.i(
                        TAG_WV,
                        "[$level] ${message.message()} (${message.sourceId()}:${message.lineNumber()})",
                    )
                    return true
                }
            }
            addJavascriptInterface(bridge, "AndroidAvatar")
            loadUrl("file:///android_asset/live2d/index.html")
            webView = this
        }
    }

    private fun bindControllerToJs() {
        stateBindJob?.cancel()
        stateBindJob = scope.launch {
            // StateFlow is already conflated/distinct — no need to wrap.
            controller.state.collect { state ->
                if (jsReady) pushStateToJs(state)
            }
        }
    }

    private fun pushStateToJs(state: AvatarUiState) {
        val wv = webView ?: return
        // Hand-rolled JS-string building blew up on caption text containing
        // newlines / quotes / control chars from real Selene replies. JSON is
        // a strict subset of JS literal syntax, so we serialise the whole
        // state payload through kotlinx.serialization and let the JS side
        // destructure it. One evaluateJavascript() per tick.
        val payload = AvatarStateJson.encodeToString(
            AvatarStatePayload(
                phase = state.phase.name.lowercase(),
                expression = state.expression,
                mouthOpenY = state.mouthOpenY,
                mouthForm = state.mouthForm,
                caption = state.caption,
            )
        )
        val js = "AvatarApi.applyState($payload);"
        wv.post { wv.evaluateJavascript(js, null) }
    }

    @Serializable
    private data class AvatarStatePayload(
        val phase: String,
        val expression: String,
        val mouthOpenY: Float,
        val mouthForm: Float,
        val caption: String?,
    )

    private fun startIdleWatcher() {
        idleWatcherJob?.cancel()
        idleWatcherJob = scope.launch {
            while (isActive) {
                delay(IDLE_POLL_INTERVAL_MS)
                val timeoutMs = container.settings.avatarIdleTimeoutMs()
                val ttsBusy = container.ttsPlayer.state.value !is TtsPlayer.State.Idle
                val idleFor = SystemClock.elapsedRealtime() - controller.lastActivityAtMs
                if (!ttsBusy && idleFor >= timeoutMs) {
                    Log.i(TAG, "idle for ${idleFor}ms (timeout=${timeoutMs}ms) — dismissing")
                    stopSelf()
                    return@launch
                }
            }
        }
    }

    private fun startForegroundCompat() {
        val notif = buildNotification(getString(R.string.avatar_overlay_notif_text))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val stopIntent = Intent(this, AvatarOverlayService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, WakeWordChannel.ID)
            .setSmallIcon(R.drawable.ic_push_small)
            .setContentTitle(getString(R.string.avatar_overlay_notif_title))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.wakeword_notif_stop), stopPi)
            .build()
    }

    override fun onDestroy() {
        idleWatcherJob?.cancel()
        stateBindJob?.cancel()
        if (::controller.isInitialized) controller.release()
        runCatching {
            val wm = getSystemService<WindowManager>()
            overlayView?.let { wm?.removeView(it) }
        }.onFailure { Log.w(TAG, "removeView failed", it) }
        // Make sure no stale viseme timeline keeps emitting after dismissal.
        runCatching { container.visemeScheduler.setTimeline(null) }
        runCatching { container.ttsPlayer.stop() }
        webView?.destroy()
        webView = null
        overlayView = null
        if (::lifecycleOwner.isInitialized) lifecycleOwner.onDestroy()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Avatar:Svc"
        private const val TAG_WV = "Avatar:WV"
        private const val NOTIF_ID = 0x41564154 // "AVAT"
        private const val IDLE_POLL_INTERVAL_MS = 500L

        private val AvatarStateJson = Json {
            encodeDefaults = true
            explicitNulls = true
        }

        const val ACTION_STOP = "ai.havencore.companion.avatar.STOP"
        const val ACTION_DEBUG_SHOW = "ai.havencore.companion.avatar.DEBUG_SHOW"
        const val EXTRA_CAPTURE_PATH = "wake_capture_path"
    }
}
