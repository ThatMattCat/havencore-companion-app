package ai.havencore.companion

import ai.havencore.companion.data.ThemeMode
import ai.havencore.companion.push.PushNotifier
import ai.havencore.companion.ui.nav.HavenNav
import ai.havencore.companion.ui.theme.HavenCoreTheme
import ai.havencore.companion.ui.theme.resolveDarkTheme
import ai.havencore.companion.wakeword.MicrophoneForegroundService
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingSessionId = MutableStateFlow<String?>(null)
    private val pendingWakeCapture = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val kiosk = intent?.getBooleanExtra(
            MicrophoneForegroundService.EXTRA_KIOSK, false,
        ) == true
        if (kiosk) setTheme(R.style.Theme_HavenCoreCompanion_Kiosk)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyKioskWindow(kiosk)
        consumeSessionExtra(intent)
        consumeWakeExtras(intent)
        val app = application as HavenCoreApp
        setContent {
            val dynamicColor by app.container.settings.dynamicColorFlow
                .collectAsState(initial = false)
            val themeMode by app.container.settings.themeModeFlow
                .collectAsState(initial = ThemeMode.System)
            HavenCoreTheme(
                darkTheme = resolveDarkTheme(themeMode),
                dynamicColor = dynamicColor,
            ) {
                HavenNav(
                    container = app.container,
                    pendingSessionId = pendingSessionId,
                    onSessionIdConsumed = { pendingSessionId.value = null },
                    pendingWakeCapturePath = pendingWakeCapture,
                    onWakeCaptureConsumed = { pendingWakeCapture.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSessionExtra(intent)
        consumeWakeExtras(intent)
    }

    private fun consumeSessionExtra(intent: Intent?) {
        val sid = intent?.getStringExtra(PushNotifier.EXTRA_SESSION_ID)
        if (!sid.isNullOrBlank()) pendingSessionId.value = sid
    }

    private fun consumeWakeExtras(intent: Intent?) {
        val kiosk = intent?.getBooleanExtra(
            MicrophoneForegroundService.EXTRA_KIOSK, false,
        ) == true
        if (kiosk) applyKioskWindow(true)
        val path = intent?.getStringExtra(MicrophoneForegroundService.EXTRA_CAPTURE_PATH)
        if (!path.isNullOrBlank()) pendingWakeCapture.value = path
    }

    // Kiosk mode flips are one-way during the activity's lifetime: once a
    // wake fires we want fullscreen + screen-on + show-when-locked until the
    // user leaves the activity. The launcher path (cold start without
    // EXTRA_KIOSK) stays with the default windowing.
    private fun applyKioskWindow(kiosk: Boolean) {
        if (!kiosk) return
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars(),
        )
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    }
}
