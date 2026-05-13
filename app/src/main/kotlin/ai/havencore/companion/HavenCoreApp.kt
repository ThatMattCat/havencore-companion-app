package ai.havencore.companion

import ai.havencore.companion.audio.MicRecorder
import ai.havencore.companion.audio.TtsPlayer
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.device.DeviceActionDispatcher
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ChatWsSession
import ai.havencore.companion.net.CompanionUploadApi
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.net.HavenCoreClient
import ai.havencore.companion.net.PushApi
import ai.havencore.companion.net.SttApi
import ai.havencore.companion.net.TtsApi
import ai.havencore.companion.push.DeviceIdProvider
import ai.havencore.companion.push.PushChannel
import ai.havencore.companion.push.PushManager
import ai.havencore.companion.push.PushNotifier
import ai.havencore.companion.wakeword.WakeWordAutostart
import ai.havencore.companion.wakeword.WakeWordChannel
import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HavenCoreApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PushChannel.register(this)
        WakeWordChannel.register(this)
        appScope.launch { WakeWordAutostart.maybeStart(this@HavenCoreApp) }
    }
}

class AppContainer(ctx: Context) {
    val appContext: Context = ctx.applicationContext
    val settings: SettingsRepository = SettingsRepository(ctx)
    val http = HavenCoreClient.build()
    val api = ConversationsApi(http)
    val chatApi = ChatApi(http)
    val ws = ChatWsSession(http)
    val sttApi = SttApi(http)
    val ttsApi = TtsApi(http)
    val companionUploadApi = CompanionUploadApi(http)
    val mic = MicRecorder(ctx)
    val ttsPlayer = TtsPlayer(ctx)
    val deviceActionDispatcher =
        DeviceActionDispatcher(appContext, settings, companionUploadApi)

    // Phase 4
    val pushApi = PushApi(http)
    val pushNotifier = PushNotifier(appContext)
    val deviceIdProvider = DeviceIdProvider(settings)
    val pushManager = PushManager(appContext, settings, pushApi, deviceIdProvider)
}
