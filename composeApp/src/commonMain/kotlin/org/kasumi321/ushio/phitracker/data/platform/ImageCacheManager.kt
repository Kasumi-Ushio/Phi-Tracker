package org.kasumi321.ushio.phitracker.data.platform

expect suspend fun clearImageCacheUrls(urls: List<String>)

expect suspend fun clearAllImageCache()

expect fun triggerAppRestart()
