package ai.havencore.companion

import ai.havencore.companion.audio.MicRecorder
import ai.havencore.companion.audio.TtsPlayer
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ChatWsSession
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.net.HavenCoreClient
import ai.havencore.companion.net.PushApi
import ai.havencore.companion.net.SttApi
import ai.havencore.companion.net.TtsApi
import ai.havencore.companion.push.DeviceIdProvider
import ai.havencore.companion.push.PushChannel
import ai.havencore.companion.push.PushNotifier
import android.app.Application
import android.content.Context

class HavenCoreApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PushChannel.register(this)
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
    val mic = MicRecorder(ctx)
    val ttsPlayer = TtsPlayer(ctx)

    // Phase 4
    val pushApi = PushApi(http)
    val pushNotifier = PushNotifier(appContext)
    val deviceIdProvider = DeviceIdProvider(settings)
}
