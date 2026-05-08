package org.kasumi321.ushio.phitracker.data.platform

import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSUserDomainMask

actual fun createPlatformPaths(): PlatformPaths {
    val documentsDir = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).first() as String
    val cachesDir = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory, NSUserDomainMask, true
    ).first() as String
    return PlatformPaths(
        filesDir = documentsDir,
        cacheDir = cachesDir
    )
}
