package org.kasumi321.ushio.phitracker.platform

import android.content.Context

actual class PathProvider(private val context: Context) {
    actual fun cacheDir(): String = context.cacheDir.absolutePath
    actual fun filesDir(): String = context.filesDir.absolutePath
}
