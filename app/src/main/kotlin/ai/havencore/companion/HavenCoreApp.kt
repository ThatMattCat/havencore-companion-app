package ai.havencore.companion

import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ConversationsApi
import ai.havencore.companion.net.HavenCoreClient
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
    val settings: SettingsRepository = SettingsRepository(ctx)
    val http = HavenCoreClient.build()
    val api = ConversationsApi(http)
    val chatApi = ChatApi(http)
}
