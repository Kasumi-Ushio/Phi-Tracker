package org.kasumi321.ushio.phitracker.data.logging

import okio.FileSystem
import okio.Path

expect object LogPathProvider {
    val fileSystem: FileSystem
    fun runtimeLogDir(): Path
    fun crashLogDir(): Path
}
