package ai.havencore.companion.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyDeviceName = stringPreferencesKey("device_name")

    val configFlow: Flow<ServerConfig> = store.data.map { prefs ->
        ServerConfig(
            baseUrl = prefs[keyBaseUrl] ?: "",
            deviceName = prefs[keyDeviceName] ?: (Build.MODEL ?: "android-phone"),
        )
    }

    suspend fun update(baseUrl: String, deviceName: String) {
        val cleaned = baseUrl.trim().trimEnd('/')
        store.edit { prefs ->
            prefs[keyBaseUrl] = cleaned
            prefs[keyDeviceName] = deviceName.trim()
        }
    }
}
