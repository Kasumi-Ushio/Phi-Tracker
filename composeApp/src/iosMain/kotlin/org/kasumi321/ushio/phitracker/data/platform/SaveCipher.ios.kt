package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.ULongVar
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess

actual fun createSaveCipher(): SaveCipher = IosSaveCipher()

@OptIn(ExperimentalForeignApi::class)
private class IosSaveCipher : SaveCipher {
    override fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = crypt(kCCDecrypt, data, key, iv)

    override fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = crypt(kCCEncrypt, data, key, iv)

    private fun crypt(operation: UInt, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = memScoped {
        val output = ByteArray(data.size + 16)
        val outputSize = alloc<ULongVar>()
        val status = key.usePinned { pinnedKey ->
            iv.usePinned { pinnedIv ->
                data.usePinned { pinnedData ->
                    output.usePinned { pinnedOutput ->
                        CCCrypt(
                            operation,
                            kCCAlgorithmAES,
                            kCCOptionPKCS7Padding,
                            pinnedKey.addressOf(0),
                            key.size.convert(),
                            pinnedIv.addressOf(0),
                            pinnedData.addressOf(0),
                            data.size.convert(),
                            pinnedOutput.addressOf(0),
                            output.size.convert(),
                            outputSize.ptr
                        )
                    }
                }
            }
        }
        check(status == kCCSuccess) { "Unable to process save cipher data: $status" }
        output.copyOf(outputSize.value.toInt())
    }
}
