package ai.havencore.companion.push

import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.PushApi
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.first
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.INSTANCE_DEFAULT
import org.unifiedpush.android.connector.UnifiedPush

sealed interface PushUi {
    data object Disabled : PushUi
    data object NoDistributor : PushUi
    data class AwaitingEndpoint(val distributorPkg: String) : PushUi
    data class Ready(val distributorPkg: String, val endpoint: String) : PushUi
    data class Failed(val reason: String) : PushUi
}

class PushManager(
    private val appContext: Context,
    private val settings: SettingsRepository,
    private val pushApi: PushApi,
    private val deviceIdProvider: DeviceIdProvider,
) {

    suspend fun enable(): Result<Unit> = runCatching {
        val distributors = UnifiedPush.getDistributors(appContext)
        if (distributors.isEmpty()) error("no UnifiedPush distributor installed")
        val pkg = UnifiedPush.getAckDistributor(appContext)
            ?: UnifiedPush.getSavedDistributor(appContext)
            ?: distributors.first()
        UnifiedPush.saveDistributor(appContext, pkg)
        settings.setPushDistributorPkg(pkg)
        settings.setPushEnabled(true)
        UnifiedPush.register(appContext, INSTANCE_DEFAULT)
        Log.i("Push:Reg", "register dispatched to $pkg")
    }

    suspend fun disable() {
        runCatching { UnifiedPush.unregister(appContext, INSTANCE_DEFAULT) }
            .onFailure { Log.w("Push:Reg", "unregister threw: ${it.message}") }
        val deviceId = settings.pushDeviceId()
        settings.setPushEnabled(false)
        settings.setPushEndpoint(null)
        settings.setPushDistributorPkg(null)
        if (deviceId != null) {
            val baseUrl = settings.configFlow.first().baseUrl
            if (baseUrl.isNotBlank()) {
                pushApi.deregister(baseUrl, deviceId).onFailure {
                    Log.w("Push:Reg", "agent deregister failed: ${it.message}")
                }
            }
        }
    }

    suspend fun onEndpoint(endpoint: String) {
        val deviceId = deviceIdProvider.get()
        val cfg = settings.configFlow.first()
        if (cfg.baseUrl.isBlank()) {
            Log.w("Push:Reg", "no baseUrl yet; persisting endpoint locally for retry")
            settings.setPushEndpoint(endpoint)
            return
        }
        pushApi.register(
            baseUrl = cfg.baseUrl,
            deviceId = deviceId,
            deviceLabel = cfg.deviceName,
            endpoint = endpoint,
        ).onSuccess {
            settings.setPushEndpoint(endpoint)
            Log.i("Push:Reg", "agent registration ok")
        }.onFailure {
            Log.w("Push:Reg", "agent registration failed: ${it.message}")
            // Persist the endpoint so a future retry can reuse it without
            // round-tripping through the distributor again.
            settings.setPushEndpoint(endpoint)
        }
    }

    suspend fun onRegistrationFailed(reason: FailedReason) {
        Log.w("Push:Reg", "registration failed: $reason; clearing local enabled flag")
        settings.setPushEnabled(false)
    }

    suspend fun onUnregistered() {
        settings.setPushEnabled(false)
        settings.setPushEndpoint(null)
        // Keep push_device_id so a final DELETE from Settings can reach the
        // agent row by id if the user re-opens Settings after this fires.
    }

    suspend fun retryAgentRegistration(): Result<Unit> = runCatching {
        val endpoint = settings.pushEndpointFlow.first()
            ?: error("no endpoint to register")
        val cfg = settings.configFlow.first()
        if (cfg.baseUrl.isBlank()) error("no baseUrl set")
        val deviceId = deviceIdProvider.get()
        pushApi.register(cfg.baseUrl, deviceId, cfg.deviceName, endpoint).getOrThrow()
    }
}
