package org.kasumi321.ushio.phitracker.data.song

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

/** Aggregate outcome of a [IllustrationPreloadCoordinator.preloadLowRes] run. */
data class PreloadResult(
    /** Attempts that finished, successful or not. */
    val completed: Int,
    /** Attempts that failed. */
    val failed: Int
) {
    val isFullySuccessful: Boolean get() = failed == 0
}

/**
 * Downloads every song's low-res illustration into persistent storage and
 * warms the in-memory cache so lists resolve instantly afterwards. Shared by
 * the home page (manual/auto-start dialog) and the onboarding wizard (inline
 * download step); callers own the settings markers (preloadDone / declined /
 * requested), this coordinator only moves files and reports progress.
 *
 * [onProgress] reports finished attempts (success or failure) against the
 * total, in completion order, plus the name of the most recently started
 * download. Cancelling the calling coroutine stops all in-flight downloads:
 * [CancellationException] is rethrown past the per-song error accounting
 * instead of being counted as a failed attempt.
 */
class IllustrationPreloadCoordinator(
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val artworkFileCache: StandardArtworkCache,
    private val thumbnailPreloader: IllustrationThumbnailPreloader
) {

    suspend fun preloadLowRes(
        concurrency: Int = 6,
        onProgress: (done: Int, total: Int, currentSongName: String?) -> Unit = { _, _, _ -> }
    ): PreloadResult = coroutineScope {
        val songs = songDataProvider.getSongs()
        val total = songs.size
        if (total == 0) {
            onProgress(0, 0, null)
            return@coroutineScope PreloadResult(completed = 0, failed = 0)
        }

        val semaphore = Semaphore(concurrency)
        val mutex = Mutex()
        var completed = 0
        var failed = 0
        var currentSongName: String? = null

        songs.keys.map { songId ->
            launch {
                semaphore.withPermit {
                    mutex.withLock {
                        currentSongName = songs[songId]?.name
                    }
                    val error = try {
                        val variants = illustrationProvider.variantDifficulties(songId)
                        if (variants.isEmpty()) {
                            val remoteUrl = illustrationProvider.getLowUrl(songId)
                            val localUri = artworkFileCache.getOrDownloadThumbnail(songId, remoteUrl)
                            // Decode once now so a corrupt/unsupported file does not
                            // receive the durable completion marker from the caller.
                            thumbnailPreloader.preload(localUri).exceptionOrNull()
                        } else {
                            preloadPerDifficultyVariants(songId, variants)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e
                    }
                    mutex.withLock {
                        if (error != null) failed++
                        completed++
                        onProgress(completed, total, currentSongName)
                    }
                }
            }
        }.forEach { it.join() }

        PreloadResult(completed = completed, failed = failed)
    }

    /**
     * Songs that ship per-difficulty jackets (see
     * [IllustrationProvider.PER_DIFFICULTY_SONGS]) have no flat slot at all:
     * every resolution lands on a variant slot (difficulty-less displays use
     * the highest one), so the preload caches exactly [variants], each under
     * its own slot. The detail page reads thumbnails from local storage only,
     * so a missing variant would show a blank header when the player switches
     * difficulty tabs. Returns the first error, if any.
     */
    private suspend fun preloadPerDifficultyVariants(
        songId: String,
        variants: List<Difficulty>
    ): Throwable? {
        for (difficulty in variants) {
            try {
                val url = illustrationProvider.getLowUrl(songId, difficulty)
                val localUri = artworkFileCache.getOrDownloadThumbnail(
                    illustrationProvider.cacheKey(songId, difficulty),
                    url
                )
                thumbnailPreloader.preload(localUri).exceptionOrNull()?.let { return it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return e
            }
        }
        return null
    }
}
