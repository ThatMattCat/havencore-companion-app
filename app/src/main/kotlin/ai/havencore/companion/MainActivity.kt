package ai.havencore.companion

import ai.havencore.companion.ui.nav.HavenNav
import ai.havencore.companion.ui.theme.HavenCoreTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as HavenCoreApp
        setContent {
            HavenCoreTheme {
                HavenNav(container = app.container)
            }
        }
    }
}
