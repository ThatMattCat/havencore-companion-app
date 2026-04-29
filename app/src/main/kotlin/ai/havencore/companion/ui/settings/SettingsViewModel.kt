package ai.havencore.companion.ui.settings

import ai.havencore.companion.data.ServerConfig
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ChatWsSession
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.net.ParsedFrame
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val ws: ChatWsSession,
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

    // Temporary debug action — Phase 1 commit 3 only. Connects, says hello,
    // logs every ParsedFrame for 30 s, then closes. Removed when ChatScreen
    // takes over the WS in commit 4 and the buttons go away in commit 5.
    fun debugTestChatWs() {
        val cfg = config.value
        if (cfg.baseUrl.isBlank()) {
            _toasts.tryEmit("Save a server URL first")
            return
        }
        viewModelScope.launch {
            _toasts.tryEmit("WS test started — check logcat (tag ChatWs)")
            val frames = ws.connect(
                scope = viewModelScope,
                baseUrl = cfg.baseUrl,
                sessionId = null,
                deviceName = cfg.deviceName,
                idleTimeout = -1,
            )
            val collector = frames.onEach { frame ->
                when (frame) {
                    is ParsedFrame.Known ->
                        Log.i("ChatWs", "frame: ${frame.event::class.simpleName} ${frame.event}")
                    is ParsedFrame.Unknown ->
                        Log.w("ChatWs", "unknown frame type=${frame.type}")
                    is ParsedFrame.Malformed ->
                        Log.w("ChatWs", "malformed: ${frame.cause.message}")
                }
            }.launchIn(viewModelScope)

            // Wait until Connected, then send "say hello".
            ws.status.first { it is ChatWsSession.Status.Connected }
            ws.send("say hello")

            delay(30_000)
            collector.cancel()
            ws.close()
            _toasts.tryEmit("WS test closed")
        }
    }
}
