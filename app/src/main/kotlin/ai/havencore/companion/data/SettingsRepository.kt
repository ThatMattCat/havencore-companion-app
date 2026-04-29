package ai.havencore.companion.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private val keyBaseUrl = stringPreferencesKey("base_url")
    private val keyDeviceName = stringPreferencesKey("device_name")
    private val keyLastSessionId = stringPreferencesKey("last_session_id")
    private val keyAutoSpeak = booleanPreferencesKey("auto_speak")

    val configFlow: Flow<ServerConfig> = store.data.map { prefs ->
        ServerConfig(
            baseUrl = prefs[keyBaseUrl] ?: "",
            deviceName = prefs[keyDeviceName] ?: (Build.MODEL ?: "android-phone"),
        )
    }

    val lastSessionIdFlow: Flow<String?> = store.data.map { prefs ->
        prefs[keyLastSessionId]?.takeIf(String::isNotBlank)
    }

    suspend fun lastSessionId(): String? = lastSessionIdFlow.first()

    suspend fun update(baseUrl: String, deviceName: String) {
        val cleaned = baseUrl.trim().trimEnd('/')
        store.edit { prefs ->
            prefs[keyBaseUrl] = cleaned
            prefs[keyDeviceName] = deviceName.trim()
        }
    }

    suspend fun setLastSessionId(sid: String?) {
        store.edit { prefs ->
            if (sid.isNullOrBlank()) {
                prefs.remove(keyLastSessionId)
            } else {
                prefs[keyLastSessionId] = sid
            }
        }
    }

    val autoSpeakFlow: Flow<Boolean> = store.data.map { prefs -> prefs[keyAutoSpeak] ?: false }

    suspend fun setAutoSpeak(on: Boolean) {
        store.edit { prefs -> prefs[keyAutoSpeak] = on }
    }
}
