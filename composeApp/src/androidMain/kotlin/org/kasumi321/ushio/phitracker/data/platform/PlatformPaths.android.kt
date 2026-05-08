package org.kasumi321.ushio.phitracker.data.platform

actual fun createPlatformPaths(): PlatformPaths {
    val context = AndroidPlatformContext.applicationContext
        ?: throw IllegalStateException(
            "AndroidPlatformContext.applicationContext not initialized. " +
                "Call AndroidPlatformContext.initialize(context) in MainActivity.onCreate before App()."
        )
    return PlatformPaths(
        filesDir = context.filesDir.absolutePath,
        cacheDir = context.cacheDir.absolutePath
    )
}
