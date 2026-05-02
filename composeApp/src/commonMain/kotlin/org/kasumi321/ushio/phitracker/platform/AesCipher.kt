package org.kasumi321.ushio.phitracker.platform

expect object AesCipher {
    fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
}
