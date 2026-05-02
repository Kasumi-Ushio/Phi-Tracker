@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.kasumi321.ushio.phitracker.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite

actual class FileStorage {
    actual fun readFile(path: String): ByteArray? {
        val file = fopen(path, "rb") ?: return null
        return try {
            fseek(file, 0, SEEK_END)
            val size = ftell(file)
            if (size < 0) return null
            fseek(file, 0, SEEK_SET)

            val bytes = ByteArray(size.toInt())
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    fread(pinned.addressOf(0), 1uL, bytes.size.toULong(), file)
                }
            }
            bytes
        } finally {
            fclose(file)
        }
    }

    actual fun writeFile(path: String, data: ByteArray) {
        val parentDir = path.substringBeforeLast('/', "")
        if (parentDir.isNotEmpty()) {
            NSFileManager.defaultManager.createDirectoryAtPath(
                parentDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }

        val file = fopen(path, "wb") ?: throw IllegalStateException("Failed to open file: $path")
        try {
            if (data.isNotEmpty()) {
                data.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1uL, data.size.toULong(), file)
                }
            }
        } finally {
            fclose(file)
        }
    }

    actual fun deleteRecursively(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}
