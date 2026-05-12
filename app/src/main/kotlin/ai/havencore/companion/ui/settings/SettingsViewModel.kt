package ai.havencore.companion.ui.settings

import ai.havencore.companion.data.ServerConfig
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.data.ThemeMode
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.push.PushManager
import ai.havencore.companion.push.PushUi
import ai.havencore.companion.voice.DefaultAssistantHelper
import ai.havencore.companion.wakeword.MicrophoneForegroundService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

sealed interface PingState {
    data object Untested : PingState
    data object InFlight : PingState
    data class Ok(val count: Int) : PingState
    data class Err(val message: String) : PingState
}

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val api: ConversationsApi,
    private val pushManager: PushManager,
    private val appContext: Context,
) : ViewModel() {

    val config: StateFlow<ServerConfig> =
        repo.configFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ServerConfig(baseUrl = "", deviceName = ""),
        )

    val silenceTimeoutMs: StateFlow<Long> =
        repo.silenceTimeoutMsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsRepository.DEFAULT_SILENCE_TIMEOUT_MS,
        )

    fun setSilenceTimeoutMs(ms: Long) {
        viewModelScope.launch { repo.setSilenceTimeoutMs(ms) }
    }

    val dynamicColor: StateFlow<Boolean> =
        repo.dynamicColorFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    fun setDynamicColor(on: Boolean) {
        viewModelScope.launch { repo.setDynamicColor(on) }
    }

    val themeMode: StateFlow<ThemeMode> =
        repo.themeModeFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.System,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    val companionCameraTakePhotoEnabled: StateFlow<Boolean> =
        repo.companionCameraTakePhotoEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun setCompanionCameraTakePhotoEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setCompanionCameraTakePhotoEnabled(enabled) }
    }

    val companionCameraIdentifyEnabled: StateFlow<Boolean> =
        repo.companionCameraIdentifyEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun setCompanionCameraIdentifyEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setCompanionCameraIdentifyEnabled(enabled) }
    }

    val companionCameraReadTextEnabled: StateFlow<Boolean> =
        repo.companionCameraReadTextEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun setCompanionCameraReadTextEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setCompanionCameraReadTextEnabled(enabled) }
    }

    val companionCameraWhoIsInViewEnabled: StateFlow<Boolean> =
        repo.companionCameraWhoIsInViewEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    fun setCompanionCameraWhoIsInViewEnabled(enabled: Boolean) {
        viewModelScope.launch { repo.setCompanionCameraWhoIsInViewEnabled(enabled) }
    }

    val wallDisplayEnabled: StateFlow<Boolean> =
        repo.wallDisplayEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    fun setWallDisplayEnabled(on: Boolean) {
        viewModelScope.launch {
            repo.setWallDisplayEnabled(on)
            val svc = Intent(appContext, MicrophoneForegroundService::class.java)
            runCatching {
                if (on) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(appContext, svc)
                    } else {
                        appContext.startService(svc)
                    }
                } else {
                    svc.action = MicrophoneForegroundService.ACTION_STOP
                    appContext.startService(svc)
                }
            }.onFailure { Log.w("SettingsVM", "wall-display service toggle failed", it) }
        }
    }

    val wakeWordThreshold: StateFlow<Float> =
        repo.wakeWordThresholdFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsRepository.DEFAULT_WAKEWORD_THRESHOLD_MILLI / 1000f,
        )

    fun setWakeWordThreshold(value: Float) {
        viewModelScope.launch { repo.setWakeWordThreshold(value) }
    }

    private val _ping = MutableStateFlow<PingState>(PingState.Untested)
    val ping: StateFlow<PingState> = _ping.asStateFlow()

    private val _isAssistantHeld = MutableStateFlow(DefaultAssistantHelper.isHeld(appContext))
    val isAssistantHeld: StateFlow<Boolean> = _isAssistantHeld.asStateFlow()

    private val refresh = MutableStateFlow(0)

    val pushUi: StateFlow<PushUi> = combine(
        repo.pushEnabledFlow,
        repo.pushEndpointFlow,
        repo.pushDistributorPkgFlow,
        refresh,
    ) { enabled, endpoint, pkg, _ ->
        when {
            UnifiedPush.getDistributors(appContext).isEmpty() -> PushUi.NoDistributor
            !enabled -> PushUi.Disabled
            endpoint != null && pkg != null -> PushUi.Ready(pkg, endpoint)
            pkg != null -> PushUi.AwaitingEndpoint(pkg)
            else -> PushUi.Failed("registration not started")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PushUi.Disabled,
    )

    fun refreshAssistantHeld() {
        _isAssistantHeld.value = DefaultAssistantHelper.isHeld(appContext)
    }

    fun onResume() {
        refresh.value++
        refreshAssistantHeld()
    }

    fun togglePush(on: Boolean) {
        viewModelScope.launch {
            if (on) pushManager.enable() else pushManager.disable()
        }
    }

    fun retryRegistration() {
        viewModelScope.launch { pushManager.retryAgentRegistration() }
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
