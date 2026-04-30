package ai.havencore.companion

import ai.havencore.companion.push.PushNotifier
import ai.havencore.companion.ui.nav.HavenNav
import ai.havencore.companion.ui.theme.HavenCoreTheme
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingSessionId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeSessionExtra(intent)
        val app = application as HavenCoreApp
        setContent {
            HavenCoreTheme {
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
