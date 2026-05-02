package org.kasumi321.ushio.phitracker.platform

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings

@OptIn(ExperimentalSettingsImplementation::class)
actual class SecureStorage {
    private val settings = KeychainSettings("org.kasumi321.ushio.phitracker")

    actual fun save(key: String, value: String) {
        settings.putString(key, value)
    }

    actual fun get(key: String): String? {
        return settings.getStringOrNull(key)
    }

    actual fun remove(key: String) {
        settings.remove(key)
    }
}
