@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.UnsafeNumber::class)

package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

actual fun createSecureKeyValueStorage(name: String): SecureKeyValueStorage =
    KeychainStorage(service = name)

@OptIn(ExperimentalForeignApi::class)
private class KeychainStorage(
    service: String
) : SecureKeyValueStorage {

    private val cfService: CFTypeRef? = CFBridgingRetain(service)

    override fun getString(key: String): String? = cfRetain(key) { cfKey ->
        val cfValue = alloc<CFTypeRefVar>()
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfKey,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne
        )
        val status = try {
            SecItemCopyMatching(query, cfValue.ptr)
        } finally {
            CFBridgingRelease(query)
        }
        if (status == errSecItemNotFound) return@cfRetain null
        check(status == errSecSuccess) { "Unable to read Keychain item: $status" }

        val data = CFBridgingRelease(cfValue.value) as? NSData ?: return@cfRetain null
        val bytes = data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) ?: return@cfRetain null
        bytes.decodeToString()
    }

    override fun putString(key: String, value: String) {
        remove(key)
        val encoded = value.encodeToByteArray()
        encoded.usePinned { pinned ->
            val data = NSData.create(
                bytes = if (encoded.isEmpty()) null else pinned.addressOf(0),
                length = encoded.size.toULong()
            )
            cfRetain(key, data) { cfKey, cfValueData ->
                val query = cfDictionaryOf(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrService to cfService,
                    kSecAttrAccount to cfKey,
                    kSecValueData to cfValueData
                )
                val status = try {
                    SecItemAdd(query, null)
                } finally {
                    CFBridgingRelease(query)
                }
                check(status == errSecSuccess) { "Unable to save Keychain item: $status" }
            }
        }
    }

    override fun remove(key: String) = cfRetain(key) { cfKey ->
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfKey
        )
        val status = try {
            SecItemDelete(query)
        } finally {
            CFBridgingRelease(query)
        }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Unable to delete Keychain item: $status"
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.cfDictionaryOf(vararg items: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
    val size = items.size
    val keys = allocArrayOf(*items.map { it.first }.toTypedArray())
    val values = allocArrayOf(*items.map { it.second }.toTypedArray())
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        size.convert(),
        null,
        null
    )
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> cfRetain(value: Any?, block: MemScope.(CFTypeRef?) -> T): T = memScoped {
    val cfValue = CFBridgingRetain(value)
    try {
        block(cfValue)
    } finally {
        CFBridgingRelease(cfValue)
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> cfRetain(value1: Any?, value2: Any?, block: MemScope.(CFTypeRef?, CFTypeRef?) -> T): T =
    memScoped {
        val cfValue1 = CFBridgingRetain(value1)
        val cfValue2 = CFBridgingRetain(value2)
        try {
            block(cfValue1, cfValue2)
        } finally {
            CFBridgingRelease(cfValue1)
            CFBridgingRelease(cfValue2)
        }
    }
