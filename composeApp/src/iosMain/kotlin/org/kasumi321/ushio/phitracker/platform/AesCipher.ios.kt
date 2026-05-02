package org.kasumi321.ushio.phitracker.platform

import korlibs.crypto.AES
import korlibs.crypto.Padding

actual object AesCipher {
    actual fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return AES.decryptAesCbc(data, key, iv, Padding.PKCS7Padding)
    }

    actual fun encrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return AES.encryptAesCbc(data, key, iv, Padding.PKCS7Padding)
    }
}
