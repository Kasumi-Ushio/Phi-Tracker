package org.kasumi321.ushio.phitracker.data.platform

interface IllustrationThumbnailPreloader {
    suspend fun preload(url: String): Result<Unit>
}

object CoilIllustrationThumbnailPreloader : IllustrationThumbnailPreloader {
    override suspend fun preload(url: String): Result<Unit> = preloadIllustrationThumbnail(url)
}

/**
 * Warms the Coil caches for [url].
 *
 * [size] and [allowHardware] must mirror the [coil3.request.ImageRequest] the
 * on-screen consumer builds: Coil's memory-cache key includes the requested
 * size, and a cached hardware bitmap is rejected by a request that disallows
 * hardware bitmaps. Only a matching entry lets the consumer's `AsyncImage`
 * resolve synchronously from memory on first composition — which the B30
 * off-screen capture relies on (see B30ImageScreen.preloadB30ExportImages).
 */
expect suspend fun preloadIllustrationThumbnail(
    url: String,
    size: Int = 168,
    allowHardware: Boolean = true
): Result<Unit>
