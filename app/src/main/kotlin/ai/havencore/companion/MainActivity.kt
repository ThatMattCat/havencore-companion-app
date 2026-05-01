package ai.havencore.companion

import ai.havencore.companion.data.ThemeMode
import ai.havencore.companion.push.PushNotifier
import ai.havencore.companion.ui.nav.HavenNav
import ai.havencore.companion.ui.theme.HavenCoreTheme
import ai.havencore.companion.ui.theme.resolveDarkTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingSessionId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeSessionExtra(intent)
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
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeSessionExtra(intent)
    }

    private fun consumeSessionExtra(intent: Intent?) {
        val sid = intent?.getStringExtra(PushNotifier.EXTRA_SESSION_ID)
        if (!sid.isNullOrBlank()) pendingSessionId.value = sid
    }
}
