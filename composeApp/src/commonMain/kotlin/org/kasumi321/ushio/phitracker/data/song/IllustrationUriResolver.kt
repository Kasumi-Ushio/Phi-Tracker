package org.kasumi321.ushio.phitracker.data.song

import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache

class IllustrationUriResolver(
    private val artworkCache: StandardArtworkCache,
    private val illustrationProvider: IllustrationProvider
) {
    fun lowUri(songId: String): String =
        artworkCache.getThumbnailIfPresent(songId) ?: illustrationProvider.getLowUrl(songId)

    fun standardUri(songId: String): String =
        artworkCache.getStandardIfPresent(songId) ?: illustrationProvider.getStandardUrl(songId)
}
