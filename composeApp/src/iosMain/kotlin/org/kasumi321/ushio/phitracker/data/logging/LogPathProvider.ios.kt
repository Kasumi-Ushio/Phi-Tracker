package org.kasumi321.ushio.phitracker.data.logging

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSUserDomainMask

actual object LogPathProvider {
    actual val fileSystem: FileSystem = FileSystem.SYSTEM

    actual fun runtimeLogDir(): Path {
        val base = platformAppSupportDir()
        return base / "runtime_logs"
    }

    actual fun crashLogDir(): Path {
        val base = platformAppSupportDir()
        return base / "crash_logs"
    }

    private fun platformAppSupportDir(): Path {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true
        )
        val appSupport = paths.firstOrNull() as? String ?: NSTemporaryDirectory()
        return "$appSupport/PhiTracker".toPath()
    }
}

private fun NSTemporaryDirectory(): String = platform.Foundation.NSTemporaryDirectory()
