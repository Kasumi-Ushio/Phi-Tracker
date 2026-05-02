package org.kasumi321.ushio.phitracker.platform

import platform.Foundation.NSHomeDirectory

actual class PathProvider {
    actual fun cacheDir(): String = NSHomeDirectory() + "/Library/Caches"

    actual fun filesDir(): String = NSHomeDirectory() + "/Documents"
}
