package ai.havencore.companion.ui.settings

import ai.havencore.companion.data.ServerConfig
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ConversationsApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val chatApi: ChatApi,
) : ViewModel() {

    val config: StateFlow<ServerConfig> =
        repo.configFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ServerConfig(baseUrl = "", deviceName = ""),
        )

    private val _ping = MutableStateFlow<PingState>(PingState.Untested)
    val ping: StateFlow<PingState> = _ping.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

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

    // Temporary debug action — Phase 1 commit 2 only. Removed once the
    // History screen lands in commit 5.
    fun debugListConversations() {
        val url = config.value.baseUrl
        if (url.isBlank()) {
            _toasts.tryEmit("Save a server URL first")
            return
        }
        viewModelScope.launch {
            val result = chatApi.listConversations(url)
            val msg = result.fold(
                onSuccess = { "Listed ${it.conversations.size} conversation(s)" },
                onFailure = {
                    "List failed: ${it.message ?: it::class.simpleName ?: "Unknown"}"
                },
            )
            _toasts.tryEmit(msg)
        }
    }
}
