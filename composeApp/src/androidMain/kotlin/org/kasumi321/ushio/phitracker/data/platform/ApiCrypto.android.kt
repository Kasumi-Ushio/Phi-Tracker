package org.kasumi321.ushio.phitracker.data.platform

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun createApiCrypto(): ApiCrypto = AndroidApiCrypto()

private class AndroidApiCrypto : ApiCrypto {
    override fun md5Hex(data: String): String = MessageDigest.getInstance("MD5")
        .digest(data.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

    override fun hmacSha1(data: String, key: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.encodeToByteArray(), "HmacSHA1"))
        return mac.doFinal(data.encodeToByteArray())
    }
}
