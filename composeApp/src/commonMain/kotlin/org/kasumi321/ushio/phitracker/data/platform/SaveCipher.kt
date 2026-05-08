package org.kasumi321.ushio.phitracker.data.platform

interface SaveCipher {
    fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
    fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray
}

expect fun createSaveCipher(): SaveCipher
