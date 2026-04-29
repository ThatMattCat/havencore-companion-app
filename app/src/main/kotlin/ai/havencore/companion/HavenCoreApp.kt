package ai.havencore.companion

import ai.havencore.companion.audio.MicRecorder
import ai.havencore.companion.audio.TtsPlayer
import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ChatWsSession
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.net.HavenCoreClient
import ai.havencore.companion.net.SttApi
import ai.havencore.companion.net.TtsApi
import android.app.Application
import android.content.Context

class HavenCoreApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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
}
