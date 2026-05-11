package org.kasumi321.ushio.phitracker.data.platform

interface IllustrationThumbnailPreloader {
    suspend fun preload(url: String): Result<Unit>
}

object CoilIllustrationThumbnailPreloader : IllustrationThumbnailPreloader {
    override suspend fun preload(url: String): Result<Unit> = preloadIllustrationThumbnail(url)
}

expect suspend fun preloadIllustrationThumbnail(url: String): Result<Unit>
