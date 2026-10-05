package org.kasumi321.ushio.phitracker.data.song

import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

class IllustrationUriResolver(
    private val artworkCache: StandardArtworkCache,
    private val illustrationProvider: IllustrationProvider
) {
    /**
     * Thumbnail URI with remote fallback: a local cache hit wins, otherwise
     * the remote low-res URL is returned and the consumer downloads on demand.
     *
     * Songs with per-difficulty jackets always resolve to a variant: the
     * `{songId}_{DIFFICULTY}` slot and per-difficulty remote URL, using
     * [difficulty] when given and the song's highest (display) difficulty
     * otherwise; other songs keep the flat cache slot and URL regardless of
     * [difficulty].
     */
    fun lowUri(songId: String, difficulty: Difficulty? = null): String =
        artworkCache.getThumbnailIfPresent(cacheKey(songId, difficulty))
            ?: illustrationProvider.getLowUrl(songId, difficulty)

    /**
     * Local-only thumbnail URI: cache hit or null, never a remote URL.
     * Callers gate this behind `illustrationPreloadDeclined` so players who
     * skipped the preload keep blank thumbnails instead of silently
     * re-downloading in lists; the Settings "re-download all illustrations"
     * action clears that flag and restores the [lowUri] fallback.
     */
    fun lowLocalUri(songId: String, difficulty: Difficulty? = null): String? =
        artworkCache.getThumbnailIfPresent(cacheKey(songId, difficulty))

    fun standardUri(songId: String, difficulty: Difficulty? = null): String =
        artworkCache.getStandardIfPresent(cacheKey(songId, difficulty))
            ?: illustrationProvider.getStandardUrl(songId, difficulty)

    private fun cacheKey(songId: String, difficulty: Difficulty?): String =
        illustrationProvider.cacheKey(songId, difficulty)
}
