package org.kasumi321.ushio.phitracker.data.logging

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

actual object LogPathProvider {
    actual val fileSystem: FileSystem = FileSystem.SYSTEM

    actual fun runtimeLogDir(): Path {
        val filesDir = platformFilesDir()
        return filesDir / "runtime_logs"
    }

    actual fun crashLogDir(): Path {
        val filesDir = platformFilesDir()
        return filesDir / "crash_logs"
    }
}

/**
 * Returns the Android app's internal files directory as an Okio [Path].
 * Must be initialised before first access (see [AndroidLogContext.init]).
 */
object AndroidLogContext {
    private var _filesDir: Path? = null
    val filesDir: Path get() = _filesDir ?: error("AndroidLogContext not initialised")

    fun init(filesDirPath: Path) {
        _filesDir = filesDirPath
    }
}

private fun platformFilesDir(): Path = AndroidLogContext.filesDir
