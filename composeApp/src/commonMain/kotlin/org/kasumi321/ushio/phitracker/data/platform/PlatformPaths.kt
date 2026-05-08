package org.kasumi321.ushio.phitracker.data.platform

data class PlatformPaths(
    val filesDir: String,
    val cacheDir: String
)

expect fun createPlatformPaths(): PlatformPaths
