package org.kasumi321.ushio.phitracker.data.song

import kotlin.test.Test
import kotlin.test.assertEquals
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache

class IllustrationUriResolverTest {
    @Test
    fun lowUriUsesLocalThumbnailBeforeRemoteFallback() {
        val cache = RecordingArtworkCache(thumbnailUri = "/local/thumbnail.png")
        val resolver = resolver(cache)

        val resolved = resolver.lowUri("song.0")

        assertEquals("/local/thumbnail.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun lowUriFallsBackToRemoteLowWhenThumbnailMissing() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.lowUri("song.0")

        assertEquals("https://example.test/illLow/song.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun standardUriUsesLocalStandardBeforeRemoteFallback() {
        val cache = RecordingArtworkCache(standardUri = "/local/standard.png")
        val resolver = resolver(cache)

        val resolved = resolver.standardUri("song.0")

        assertEquals("/local/standard.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    @Test
    fun standardUriFallsBackToRemoteStandardWhenArtworkMissing() {
        val cache = RecordingArtworkCache()
        val resolver = resolver(cache)

        val resolved = resolver.standardUri("song.0")

        assertEquals("https://example.test/ill/song.png", resolved)
        assertEquals(0, cache.downloadCalls)
    }

    private fun resolver(cache: StandardArtworkCache): IllustrationUriResolver {
        val provider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
        return IllustrationUriResolver(cache, provider)
    }

    private class RecordingArtworkCache(
        private val thumbnailUri: String? = null,
        private val standardUri: String? = null
    ) : StandardArtworkCache {
        var downloadCalls = 0
            private set

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            downloadCalls += 1
            return url
        }

        override fun getThumbnailIfPresent(songId: String): String? = thumbnailUri
        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit

        override suspend fun getOrDownloadStandard(songId: String, url: String): String {
            downloadCalls += 1
            return url
        }

        override fun getStandardIfPresent(songId: String): String? = standardUri
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }
}
