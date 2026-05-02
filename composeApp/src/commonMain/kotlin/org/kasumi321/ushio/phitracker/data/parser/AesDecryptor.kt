package org.kasumi321.ushio.phitracker.data.parser

import org.kasumi321.ushio.phitracker.data.api.CryptoConstants
import org.kasumi321.ushio.phitracker.platform.AesCipher

class AesDecryptor {

    fun decrypt(data: ByteArray): Pair<Int, ByteArray> {
        val version = data[0].toInt() and 0xFF
        val cipherText = data.copyOfRange(1, data.size)
        val decrypted = AesCipher.decrypt(cipherText, CryptoConstants.AES_KEY, CryptoConstants.AES_IV)
        return version to decrypted
    }

    fun encrypt(version: Int, data: ByteArray): ByteArray {
        val encrypted = AesCipher.encrypt(data, CryptoConstants.AES_KEY, CryptoConstants.AES_IV)
        return byteArrayOf(version.toByte()) + encrypted
    }
}
