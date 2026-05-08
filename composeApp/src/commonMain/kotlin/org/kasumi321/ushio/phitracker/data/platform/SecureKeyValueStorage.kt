package org.kasumi321.ushio.phitracker.data.platform

interface SecureKeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

expect fun createSecureKeyValueStorage(name: String): SecureKeyValueStorage
