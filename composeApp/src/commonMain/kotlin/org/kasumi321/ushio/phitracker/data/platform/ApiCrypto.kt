package org.kasumi321.ushio.phitracker.data.platform

interface ApiCrypto {
    fun md5Hex(data: String): String
    fun hmacSha1(data: String, key: String): ByteArray
}

expect fun createApiCrypto(): ApiCrypto
