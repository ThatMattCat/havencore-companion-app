package ai.havencore.companion.ui.settings

import ai.havencore.companion.data.ServerConfig
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.voice.DefaultAssistantHelper
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PingState {
    data object Untested : PingState
    data object InFlight : PingState
    data class Ok(val count: Int) : PingState
    data class Err(val message: String) : PingState
}

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val api: ConversationsApi,
    private val appContext: Context,
) : ViewModel() {

    val config: StateFlow<ServerConfig> =
        repo.configFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ServerConfig(baseUrl = "", deviceName = ""),
        )

    private val _ping = MutableStateFlow<PingState>(PingState.Untested)
    val ping: StateFlow<PingState> = _ping.asStateFlow()

    private val _isAssistantHeld = MutableStateFlow(DefaultAssistantHelper.isHeld(appContext))
    val isAssistantHeld: StateFlow<Boolean> = _isAssistantHeld.asStateFlow()

    fun refreshAssistantHeld() {
        _isAssistantHeld.value = DefaultAssistantHelper.isHeld(appContext)
    }

    fun save(baseUrl: String, deviceName: String) {
        viewModelScope.launch {
            repo.update(baseUrl, deviceName)
            _ping.value = PingState.Untested
        }
    }

    fun testConnection() {
        val url = config.value.baseUrl
        if (url.isBlank()) {
            _ping.value = PingState.Err("Save a server URL first")
            return
        }
        _ping.value = PingState.InFlight
        viewModelScope.launch {
            val result = api.ping(url)
            _ping.value = result.fold(
                onSuccess = { PingState.Ok(it) },
                onFailure = {
                    PingState.Err(it.message ?: it::class.simpleName ?: "Unknown error")
                },
            )
        }
    }
}
