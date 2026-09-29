package org.kasumi321.ushio.phitracker.data.platform

expect suspend fun clearImageCacheUrls(urls: List<String>)

expect suspend fun clearAllImageCache()

/**
 * Asks the user to restart the app (or force-restarts it where the platform
 * allows). [title] and [message] are the caller-localized prompt texts; they
 * are only displayed on platforms that cannot restart on their own (iOS).
 */
expect fun triggerAppRestart(title: String, message: String)
