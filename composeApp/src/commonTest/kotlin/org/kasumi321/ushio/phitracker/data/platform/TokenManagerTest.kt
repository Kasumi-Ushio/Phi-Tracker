package org.kasumi321.ushio.phitracker.data.platform

import org.kasumi321.ushio.phitracker.domain.model.Server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenManagerTest {

    @Test
    fun saveTrimsTokenAndStoresBothKeys() {
        val storage = FakeSecureKeyValueStorage()
        val manager = TokenManager(storage)

        manager.saveToken("  abc123  ", Server.GLOBAL)

        assertEquals("abc123", storage.map["session_token"])
        assertEquals("GLOBAL", storage.map["server"])
        val result = manager.getToken()
        assertEquals("abc123", result?.first)
        assertEquals(Server.GLOBAL, result?.second)
    }

    @Test
    fun getReturnsNullWhenNoTokenStored() {
        val storage = FakeSecureKeyValueStorage()
        val manager = TokenManager(storage)

        assertNull(manager.getToken())
    }

    @Test
    fun getReturnsEmptyStringForExplicitEmptyToken() {
        val storage = FakeSecureKeyValueStorage()
        storage.map["session_token"] = ""
        val manager = TokenManager(storage)

        val result = manager.getToken()
        assertEquals("", result?.first)
        assertEquals(Server.CN, result?.second)
    }

    @Test
    fun serverDefaultsToCNWhenKeyMissing() {
        val storage = FakeSecureKeyValueStorage()
        storage.map["session_token"] = "abc123"
        val manager = TokenManager(storage)

        val result = manager.getToken()
        assertEquals("abc123", result?.first)
        assertEquals(Server.CN, result?.second)
    }

    @Test
    fun serverDefaultsToCNWhenValueInvalid() {
        val storage = FakeSecureKeyValueStorage()
        storage.map["session_token"] = "abc123"
        storage.map["server"] = "INVALID_SERVER"
        val manager = TokenManager(storage)

        val result = manager.getToken()
        assertEquals("abc123", result?.first)
        assertEquals(Server.CN, result?.second)
    }

    @Test
    fun clearRemovesBothKeysMakesGetReturnNull() {
        val storage = FakeSecureKeyValueStorage()
        val manager = TokenManager(storage)
        manager.saveToken("abc123", Server.CN)

        manager.clearToken()

        assertNull(storage.map["session_token"])
        assertNull(storage.map["server"])
        assertNull(manager.getToken())
    }
}

class FakeSecureKeyValueStorage : SecureKeyValueStorage {
    val map = mutableMapOf<String, String>()

    override fun getString(key: String): String? = map[key]

    override fun putString(key: String, value: String) {
        map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}
