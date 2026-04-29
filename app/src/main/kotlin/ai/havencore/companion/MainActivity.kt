package ai.havencore.companion

import ai.havencore.companion.ui.settings.SettingsScreen
import ai.havencore.companion.ui.settings.SettingsViewModel
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
        val factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    app.container.settings,
                    app.container.api,
                    app.container.chatApi,
                    app.container.ws,
                )
            }
        }
        val vm = ViewModelProvider(this, factory)[SettingsViewModel::class.java]
        setContent {
            HavenCoreTheme {
                SettingsScreen(vm)
            }
        }
    }
}
