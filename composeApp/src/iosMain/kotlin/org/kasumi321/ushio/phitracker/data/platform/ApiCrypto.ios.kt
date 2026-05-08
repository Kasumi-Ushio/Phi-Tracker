package org.kasumi321.ushio.phitracker.data.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.UByteVarOf
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA1

actual fun createApiCrypto(): ApiCrypto = IosApiCrypto()

@OptIn(ExperimentalForeignApi::class)
private class IosApiCrypto : ApiCrypto {
    override fun md5Hex(data: String): String {
        val input = data.encodeToByteArray()
        val output = ByteArray(CC_MD5_DIGEST_LENGTH)
        input.usePinned { pinnedInput ->
            output.usePinned { pinnedOutput ->
                CC_MD5(pinnedInput.addressOf(0).reinterpret<UByteVarOf<UByte>>(), input.size.convert(), pinnedOutput.addressOf(0).reinterpret<UByteVarOf<UByte>>())
            }
        }
        return output.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    }

    override fun hmacSha1(data: String, key: String): ByteArray {
        val input = data.encodeToByteArray()
        val keyBytes = key.encodeToByteArray()
        val output = ByteArray(20)
        keyBytes.usePinned { pinnedKey ->
            input.usePinned { pinnedInput ->
                output.usePinned { pinnedOutput ->
                    CCHmac(
                        kCCHmacAlgSHA1,
                        pinnedKey.addressOf(0).reinterpret<UByteVarOf<UByte>>(),
                        keyBytes.size.convert(),
                        pinnedInput.addressOf(0).reinterpret<UByteVarOf<UByte>>(),
                        input.size.convert(),
                        pinnedOutput.addressOf(0).reinterpret<UByteVarOf<UByte>>()
                    )
                }
            }
        }
        return output
    }
}
