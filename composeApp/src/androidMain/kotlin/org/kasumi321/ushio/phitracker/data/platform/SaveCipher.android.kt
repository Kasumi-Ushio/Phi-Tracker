package org.kasumi321.ushio.phitracker.data.platform

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual fun createSaveCipher(): SaveCipher = AndroidSaveCipher()

private class AndroidSaveCipher : SaveCipher {
    override fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = crypt(Cipher.DECRYPT_MODE, data, key, iv)

    override fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray = crypt(Cipher.ENCRYPT_MODE, data, key, iv)

    private fun crypt(mode: Int, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }
}
