@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package org.kasumi321.ushio.phitracker.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

actual class AssetReader {
    actual fun readAsset(name: String): ByteArray {
        val path = resolveResourcePath(name)
            ?: throw IllegalArgumentException("Asset not found: $name")
        return readFileBytes(path)
            ?: throw IllegalStateException("Failed to read asset: $name")
    }

    private fun resolveResourcePath(name: String): String? {
        NSBundle.mainBundle.pathForResource(name, null)?.let { return it }

        val directory = name.substringBeforeLast('/', "")
        val filename = name.substringAfterLast('/')
        val dotIndex = filename.lastIndexOf('.')
        val resourceName = if (dotIndex >= 0) filename.substring(0, dotIndex) else filename
        val extension = if (dotIndex >= 0) filename.substring(dotIndex + 1) else null

        return NSBundle.mainBundle.pathForResource(
            name = resourceName,
            ofType = extension,
            inDirectory = directory.ifEmpty { null },
        )
    }

    private fun readFileBytes(path: String): ByteArray? {
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
}
