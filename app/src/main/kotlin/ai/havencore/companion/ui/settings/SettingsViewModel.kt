package ai.havencore.companion.ui.settings

import ai.havencore.companion.audio.MicRecorder
import ai.havencore.companion.data.ServerConfig
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.net.TtsApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
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

// Phase 2 step 2 debug surface; removed in step 6 alongside the other
// temporary debug buttons.
sealed interface TtsTestState {
    data object Untested : TtsTestState
    data object InFlight : TtsTestState
    data class Ok(val bytes: Int, val contentType: String) : TtsTestState
    data class Err(val message: String) : TtsTestState
}

// Phase 2 step 3 debug surface; removed in step 6.
sealed interface MicTestState {
    data object Untested : MicTestState
    data object Recording : MicTestState
    data class Ok(val bytes: Long, val path: String) : MicTestState
    data class Err(val message: String) : MicTestState
}

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val api: ConversationsApi,
    private val ttsApi: TtsApi,
    private val mic: MicRecorder,
) : ViewModel() {

    val config: StateFlow<ServerConfig> =
        repo.configFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ServerConfig(baseUrl = "", deviceName = ""),
        )

    private val _ping = MutableStateFlow<PingState>(PingState.Untested)
    val ping: StateFlow<PingState> = _ping.asStateFlow()

    private val _ttsTest = MutableStateFlow<TtsTestState>(TtsTestState.Untested)
    val ttsTest: StateFlow<TtsTestState> = _ttsTest.asStateFlow()

    private val _micTest = MutableStateFlow<MicTestState>(MicTestState.Untested)
    val micTest: StateFlow<MicTestState> = _micTest.asStateFlow()

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

    fun testMic() {
        _micTest.value = MicTestState.Recording
        viewModelScope.launch {
            val started = mic.start()
            if (started.isFailure) {
                _micTest.value = MicTestState.Err(
                    started.exceptionOrNull()?.message ?: "start failed",
                )
                return@launch
            }
            delay(3_000)
            val stopped = mic.stop()
            _micTest.value = stopped.fold(
                onSuccess = { f -> MicTestState.Ok(bytes = f.length(), path = f.absolutePath) },
                onFailure = {
                    MicTestState.Err(it.message ?: it::class.simpleName ?: "stop failed")
                },
            )
        }
    }

    fun testTts() {
        val url = config.value.baseUrl
        if (url.isBlank()) {
            _ttsTest.value = TtsTestState.Err("Save a server URL first")
            return
        }
        _ttsTest.value = TtsTestState.InFlight
        viewModelScope.launch {
            val result = ttsApi.speak(url, "hello from havencore companion")
            _ttsTest.value = result.fold(
                onSuccess = { TtsTestState.Ok(bytes = it.bytes.size, contentType = it.contentType) },
                onFailure = {
                    TtsTestState.Err(it.message ?: it::class.simpleName ?: "Unknown error")
                },
            )
        }
    }
}
