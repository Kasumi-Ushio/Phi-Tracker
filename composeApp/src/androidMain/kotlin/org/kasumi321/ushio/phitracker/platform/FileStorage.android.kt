package org.kasumi321.ushio.phitracker.platform

import java.io.File

actual class FileStorage {
    actual fun readFile(path: String): ByteArray? {
        val file = File(path)
        return if (file.exists()) file.readBytes() else null
    }

    actual fun writeFile(path: String, data: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    actual fun deleteRecursively(path: String) {
        File(path).deleteRecursively()
    }
}
