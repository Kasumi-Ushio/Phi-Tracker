package org.kasumi321.ushio.phitracker.platform

expect class SecureStorage {
    fun save(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}
