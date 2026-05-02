package org.kasumi321.ushio.phitracker.platform

expect class PathProvider {
    fun cacheDir(): String
    fun filesDir(): String
}
