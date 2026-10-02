package org.kasumi321.ushio.phitracker.data.song

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IllustrationPreloadCoordinatorTest {

    @Test
    fun progressCountsEveryFinishedAttemptAgainstTotal() = runTest {
        val preloader = RecordingPreloader()
        val coordinator = coordinator(preloader)
        val events = mutableListOf<Pair<Int, Int>>()

        val result = coordinator.preloadLowRes { done, total, _ -> events += done to total }

        assertEquals(PreloadResult(completed = 2, failed = 0), result)
        assertTrue(result.isFullySuccessful)
        assertEquals(listOf(1 to 2, 2 to 2), events)
        assertEquals(
            listOf("https://example.test/illLow/song-a.png", "https://example.test/illLow/song-b.png"),
            preloader.urls.sorted()
        )
    }

    @Test
    fun progressReportsTheMostRecentlyStartedSongName() = runTest {
        val coordinator = coordinator(RecordingPreloader())
        val events = mutableListOf<Triple<Int, Int, String?>>()

        coordinator.preloadLowRes { done, total, song -> events += Triple(done, total, song) }

        assertEquals(
            listOf<Triple<Int, Int, String?>>(Triple(1, 2, "Song A"), Triple(2, 2, "Song B")),
            events
        )
    }

    @Test
    fun partialFailureIsAggregatedAndDoesNotStopOtherSongs() = runTest {
        val preloader = RecordingPreloader(failOnUrl = "https://example.test/illLow/song-b.png")
        val coordinator = coordinator(preloader)
        val events = mutableListOf<Pair<Int, Int>>()

        val result = coordinator.preloadLowRes { done, total, _ -> events += done to total }

        assertEquals(PreloadResult(completed = 2, failed = 1), result)
        assertFalse(result.isFullySuccessful)
        assertEquals(listOf(1 to 2, 2 to 2), events, "progress must reach the total even with failures")
    }

    @Test
    fun cacheDownloadFailureCountsAsFailedAttempt() = runTest {
        val coordinator = coordinator(
            preloader = RecordingPreloader(),
            artworkCache = object : PassthroughArtworkCache() {
                override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
                    if (songId == "song-a.0") error("download failed")
                    return super.getOrDownloadThumbnail(songId, url)
                }
            }
        )

        val result = coordinator.preloadLowRes()

        assertEquals(PreloadResult(completed = 2, failed = 1), result)
    }

    @Test
    fun emptySongListCompletesImmediatelyWithZeroProgress() = runTest {
        val coordinator = coordinator(
            preloader = RecordingPreloader(),
            songDataProvider = SongDataProvider(HeaderOnlyAssetReader, testPaths)
        )
        val events = mutableListOf<Pair<Int, Int>>()

        val result = coordinator.preloadLowRes { done, total, _ -> events += done to total }

        assertEquals(PreloadResult(completed = 0, failed = 0), result)
        assertTrue(result.isFullySuccessful)
        assertEquals(listOf(0 to 0), events)
    }

    @Test
    fun cancellingTheCallerStopsProgressAndRethrows() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val preloader = object : IllustrationThumbnailPreloader {
            override suspend fun preload(url: String): Result<Unit> {
                entered.complete(Unit)
                release.await()
                return Result.success(Unit)
            }
        }
        val coordinator = coordinator(preloader)
        val events = mutableListOf<Pair<Int, Int>>()

        val job = launch {
            coordinator.preloadLowRes { done, total, _ -> events += done to total }
        }
        entered.await()
        runCurrent()
        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled, "cancellation must propagate out of preloadLowRes")
        assertTrue(events.isEmpty(), "no attempt finished, so progress must not advance")
    }

    private fun coordinator(
        preloader: IllustrationThumbnailPreloader,
        songDataProvider: SongDataProvider = SongDataProvider(TwoSongAssetReader, testPaths),
        artworkCache: StandardArtworkCache = PassthroughArtworkCache()
    ) = IllustrationPreloadCoordinator(
        songDataProvider = songDataProvider,
        illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
        artworkFileCache = artworkCache,
        thumbnailPreloader = preloader
    )

    private open class PassthroughArtworkCache : StandardArtworkCache {
        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String = url
        override fun getThumbnailIfPresent(songId: String): String? = null
        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit
        override suspend fun getOrDownloadStandard(songId: String, url: String): String = url
        override fun getStandardIfPresent(songId: String): String? = null
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private class RecordingPreloader(
        private val failOnUrl: String? = null
    ) : IllustrationThumbnailPreloader {
        val urls = mutableListOf<String>()

        override suspend fun preload(url: String): Result<Unit> {
            urls += url
            if (url == failOnUrl) return Result.failure(IllegalStateException("preload failed"))
            return Result.success(Unit)
        }
    }

    private object TwoSongAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\nsong-a\tSong A\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-b\tSong B\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0"
            "infolist.json", "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    private object HeaderOnlyAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT"
            "infolist.json", "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    private companion object {
        val testPaths = PlatformPaths("/tmp/phi_preload_coordinator_test", "/tmp/phi_preload_coordinator_test_cache")
    }
}
