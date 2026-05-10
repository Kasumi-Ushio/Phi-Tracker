package org.kasumi321.ushio.phitracker.data.platform

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache

actual suspend fun clearImageCacheUrls(urls: List<String>) {
    val imageLoader = SingletonImageLoader.get(PlatformContext.INSTANCE)
    val diskCache = imageLoader.diskCache
    val memoryCache = imageLoader.memoryCache
    for (url in urls) {
        diskCache?.remove(url)
        memoryCache?.remove(MemoryCache.Key(url))
    }
}

actual suspend fun clearAllImageCache() {
    val imageLoader = SingletonImageLoader.get(PlatformContext.INSTANCE)
    imageLoader.diskCache?.clear()
    imageLoader.memoryCache?.clear()
}

actual fun triggerAppRestart() {
    showNativeAlert(
        title = "需要手动重启",
        message = "iOS 平台暂不支持自动关闭应用，请手动重启应用。",
    )
}
