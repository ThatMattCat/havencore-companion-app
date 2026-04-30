package ai.havencore.companion.push

import ai.havencore.companion.data.SettingsRepository
import java.util.UUID

class DeviceIdProvider(private val settings: SettingsRepository) {
    suspend fun get(): String {
        settings.pushDeviceId()?.let { return it }
        val fresh = UUID.randomUUID().toString()
        settings.setPushDeviceId(fresh)
        return fresh
    }
}
