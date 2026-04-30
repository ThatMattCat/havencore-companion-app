package ai.havencore.companion.ui.nav

import ai.havencore.companion.AppContainer
import ai.havencore.companion.data.ServerConfig
import ai.havencore.companion.ui.chat.ChatScreen
import ai.havencore.companion.ui.chat.ChatViewModel
import ai.havencore.companion.ui.history.HistoryScreen
import ai.havencore.companion.ui.history.HistoryViewModel
import ai.havencore.companion.ui.settings.SettingsScreen
import ai.havencore.companion.ui.settings.SettingsViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first

@Composable
fun HavenNav(container: AppContainer) {
    // Read the persisted ServerConfig once before deciding the start
    // destination; otherwise collectAsState's synchronous initial value would
    // route returning users with a saved baseUrl through Settings briefly on
    // every launch.
    val initialCfg by produceState<ServerConfig?>(initialValue = null) {
        value = container.settings.configFlow.first()
    }
    val cfg = initialCfg ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val nav = rememberNavController()
    val start = if (cfg.baseUrl.isBlank()) "settings" else "history"

    NavHost(navController = nav, startDestination = start) {

        composable("settings") {
            val vm: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            container.settings,
                            container.api,
                            container.pushManager,
                            container.appContext,
                        )
                    }
                },
            )
            SettingsScreen(
                vm = vm,
                onBack = {
                    if (!nav.popBackStack()) {
                        // First-launch path: nothing to pop. If baseUrl is
                        // now set, jump to history and burn the settings
                        // entry so back-from-history exits the app.
                        if (vm.config.value.baseUrl.isNotBlank()) {
                            nav.navigate("history") {
                                popUpTo("settings") { inclusive = true }
                            }
                        }
                    }
                },
            )
        }

        composable("history") {
            val vm: HistoryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        HistoryViewModel(container.settings, container.chatApi)
                    }
                },
            )
            HistoryScreen(
                vm = vm,
                onOpen = { sid -> nav.navigate("chat?sessionId=$sid") },
                onNew = { nav.navigate("chat") },
                onOpenSettings = { nav.navigate("settings") },
            )
        }

        composable(
            route = "chat?sessionId={sessionId}",
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val sid = entry.arguments?.getString("sessionId")
            val vm: ChatViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ChatViewModel(
                            settings = container.settings,
                            chatApi = container.chatApi,
                            ws = container.ws,
                            sttApi = container.sttApi,
                            ttsApi = container.ttsApi,
                            mic = container.mic,
                            ttsPlayer = container.ttsPlayer,
                            sessionToResume = sid,
                        )
                    }
                },
            )
            ChatScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate("settings") },
            )
        }
    }
}
