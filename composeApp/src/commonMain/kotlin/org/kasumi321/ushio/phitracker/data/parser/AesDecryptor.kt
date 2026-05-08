package org.kasumi321.ushio.phitracker.data.parser

import org.kasumi321.ushio.phitracker.data.api.CryptoConstants
import org.kasumi321.ushio.phitracker.data.platform.SaveCipher
import org.kasumi321.ushio.phitracker.data.platform.createSaveCipher

class AesDecryptor(
    private val cipher: SaveCipher = createSaveCipher()
) {
    fun decrypt(data: ByteArray): Pair<Int, ByteArray> {
        val version = data[0].toInt() and 0xFF
        val cipherText = data.copyOfRange(1, data.size)
        return version to cipher.decrypt(cipherText, CryptoConstants.AES_KEY, CryptoConstants.AES_IV)
    }

    fun encrypt(version: Int, data: ByteArray): ByteArray {
        return byteArrayOf(version.toByte()) + cipher.encrypt(data, CryptoConstants.AES_KEY, CryptoConstants.AES_IV)
    }
}
