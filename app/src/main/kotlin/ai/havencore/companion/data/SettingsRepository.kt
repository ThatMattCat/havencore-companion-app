package ai.havencore.companion.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
    private val keyAssistPromptSeen = booleanPreferencesKey("default_assistant_prompt_seen")
    private val keyPushEnabled = booleanPreferencesKey("push_enabled")
    private val keyPushDeviceId = stringPreferencesKey("push_device_id")
    private val keyPushEndpoint = stringPreferencesKey("push_endpoint")
    private val keyPushDistributorPkg = stringPreferencesKey("push_distributor_pkg")
    private val keySilenceTimeoutMs = longPreferencesKey("silence_timeout_ms")
    private val keyDynamicColor = booleanPreferencesKey("dynamic_color")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyCompanionCameraTakePhotoEnabled =
        booleanPreferencesKey("companion_camera_take_photo_enabled")
    private val keyCompanionCameraIdentifyEnabled =
        booleanPreferencesKey("companion_camera_identify_enabled")
    private val keyCompanionCameraReadTextEnabled =
        booleanPreferencesKey("companion_camera_read_text_enabled")
    private val keyCompanionCameraWhoIsInViewEnabled =
        booleanPreferencesKey("companion_camera_who_is_in_view_enabled")

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

    val dynamicColorFlow: Flow<Boolean> =
        store.data.map { prefs -> prefs[keyDynamicColor] ?: false }

    suspend fun setDynamicColor(on: Boolean) {
        store.edit { prefs -> prefs[keyDynamicColor] = on }
    }

    val themeModeFlow: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.fromKey(prefs[keyThemeMode])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { prefs -> prefs[keyThemeMode] = mode.key }
    }

    val defaultAssistantPromptSeenFlow: Flow<Boolean> =
        store.data.map { prefs -> prefs[keyAssistPromptSeen] ?: false }

    suspend fun setDefaultAssistantPromptSeen(seen: Boolean) {
        store.edit { prefs -> prefs[keyAssistPromptSeen] = seen }
    }

    val pushEnabledFlow: Flow<Boolean> = store.data.map { prefs -> prefs[keyPushEnabled] ?: false }

    val pushDeviceIdFlow: Flow<String?> = store.data.map { prefs -> prefs[keyPushDeviceId] }

    val pushEndpointFlow: Flow<String?> = store.data.map { prefs -> prefs[keyPushEndpoint] }

    val pushDistributorPkgFlow: Flow<String?> =
        store.data.map { prefs -> prefs[keyPushDistributorPkg] }

    suspend fun pushDeviceId(): String? = pushDeviceIdFlow.first()

    suspend fun setPushDeviceId(id: String) {
        store.edit { prefs -> prefs[keyPushDeviceId] = id }
    }

    suspend fun setPushEnabled(on: Boolean) {
        store.edit { prefs -> prefs[keyPushEnabled] = on }
    }

    suspend fun setPushEndpoint(url: String?) {
        store.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(keyPushEndpoint) else prefs[keyPushEndpoint] = url
        }
    }

    suspend fun setPushDistributorPkg(pkg: String?) {
        store.edit { prefs ->
            if (pkg.isNullOrBlank()) {
                prefs.remove(keyPushDistributorPkg)
            } else {
                prefs[keyPushDistributorPkg] = pkg
            }
        }
    }

    val silenceTimeoutMsFlow: Flow<Long> =
        store.data.map { prefs -> prefs[keySilenceTimeoutMs] ?: DEFAULT_SILENCE_TIMEOUT_MS }

    suspend fun silenceTimeoutMs(): Long = silenceTimeoutMsFlow.first()

    suspend fun setSilenceTimeoutMs(ms: Long) {
        val clamped = ms.coerceIn(MIN_SILENCE_TIMEOUT_MS, MAX_SILENCE_TIMEOUT_MS)
        store.edit { prefs -> prefs[keySilenceTimeoutMs] = clamped }
    }

    // Companion-app camera tools.
    //
    // The take_photo flow is the master gate (no separate toggle for it on the
    // Settings card — its switch IS the master). Vision-chained tools each
    // have their own switch underneath; they require the master to be on,
    // and additionally honor their own toggle for fine-grained control
    // (e.g. user wants take_photo but not OCR). All default ON so a fresh
    // install gets every camera capability after granting CAMERA.
    val companionCameraTakePhotoEnabledFlow: Flow<Boolean> =
        store.data.map { prefs -> prefs[keyCompanionCameraTakePhotoEnabled] ?: true }

    suspend fun setCompanionCameraTakePhotoEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[keyCompanionCameraTakePhotoEnabled] = enabled }
    }

    val companionCameraIdentifyEnabledFlow: Flow<Boolean> =
        store.data.map { prefs -> prefs[keyCompanionCameraIdentifyEnabled] ?: true }

    suspend fun setCompanionCameraIdentifyEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[keyCompanionCameraIdentifyEnabled] = enabled }
    }

    val companionCameraReadTextEnabledFlow: Flow<Boolean> =
        store.data.map { prefs -> prefs[keyCompanionCameraReadTextEnabled] ?: true }

    suspend fun setCompanionCameraReadTextEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[keyCompanionCameraReadTextEnabled] = enabled }
    }

    val companionCameraWhoIsInViewEnabledFlow: Flow<Boolean> =
        store.data.map { prefs -> prefs[keyCompanionCameraWhoIsInViewEnabled] ?: true }

    suspend fun setCompanionCameraWhoIsInViewEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[keyCompanionCameraWhoIsInViewEnabled] = enabled }
    }

    companion object {
        const val DEFAULT_SILENCE_TIMEOUT_MS = 2000L
        const val MIN_SILENCE_TIMEOUT_MS = 600L
        const val MAX_SILENCE_TIMEOUT_MS = 4000L
        const val SILENCE_TIMEOUT_STEP_MS = 200L
    }
}
