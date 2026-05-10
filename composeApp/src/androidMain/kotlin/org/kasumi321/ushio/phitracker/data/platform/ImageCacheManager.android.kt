package org.kasumi321.ushio.phitracker.data.platform

import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

actual suspend fun clearImageCacheUrls(urls: List<String>) {
    val context = AndroidPlatformContext.applicationContext ?: return
    val imageLoader = SingletonImageLoader.get(context)
    val diskCache = imageLoader.diskCache
    val memoryCache = imageLoader.memoryCache
    for (url in urls) {
        diskCache?.remove(url)
        memoryCache?.remove(MemoryCache.Key(url))
    }
}

actual suspend fun clearAllImageCache() {
    val context = AndroidPlatformContext.applicationContext ?: return
    val imageLoader = SingletonImageLoader.get(context)
    imageLoader.diskCache?.clear()
    imageLoader.memoryCache?.clear()
}

actual fun triggerAppRestart() {
    kotlin.system.exitProcess(0)
}
