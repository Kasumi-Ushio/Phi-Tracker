package org.kasumi321.ushio.phitracker.data.song

import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache

class IllustrationUriResolver(
    private val artworkCache: StandardArtworkCache,
    private val illustrationProvider: IllustrationProvider
) {
    /**
     * Thumbnail URI with remote fallback: a local cache hit wins, otherwise
     * the remote low-res URL is returned and the consumer downloads on demand.
     */
    fun lowUri(songId: String): String =
        artworkCache.getThumbnailIfPresent(songId) ?: illustrationProvider.getLowUrl(songId)

    /**
     * Local-only thumbnail URI: cache hit or null, never a remote URL.
     * Callers gate this behind `illustrationPreloadDeclined` so players who
     * skipped the preload keep blank thumbnails instead of silently
     * re-downloading in lists; the Settings "re-download all illustrations"
     * action clears that flag and restores the [lowUri] fallback.
     */
    fun lowLocalUri(songId: String): String? =
        artworkCache.getThumbnailIfPresent(songId)

    fun standardUri(songId: String): String =
        artworkCache.getStandardIfPresent(songId) ?: illustrationProvider.getStandardUrl(songId)
}
