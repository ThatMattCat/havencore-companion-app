package ai.havencore.companion

import ai.havencore.companion.ui.chat.ChatScreen
import ai.havencore.companion.ui.chat.ChatViewModel
import ai.havencore.companion.ui.theme.HavenCoreTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HavenCoreApp
        // Phase 1 commit 4: hard-coded chat route. Commit 5 replaces this
        // with HavenNav (history list as start, chat as a route arg).
        val factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    settings = app.container.settings,
                    chatApi = app.container.chatApi,
                    ws = app.container.ws,
                    sessionToResume = null,
                )
            }
        }
        val vm = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        setContent {
            HavenCoreTheme {
                ChatScreen(
                    vm = vm,
                    onBack = { /* no-op until commit 5 wires nav */ },
                    onOpenSettings = { /* no-op until commit 5 wires nav */ },
                )
            }
        }
    }
}
