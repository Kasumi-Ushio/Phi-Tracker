package org.kasumi321.ushio.phitracker.data.song

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.clearImageCacheUrls

/**
 * Shared song-data update flow used by both the settings entry and the songs
 * tab pull-to-refresh: downloads the four data files, then reconciles the
 * illustration cache for added/removed songs, reporting per-phase progress.
 */
class SongDataUpdateCoordinator(
    private val songDataUpdater: SongDataUpdater,
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val artworkFileCache: StandardArtworkCache,
    private val thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader,
    private val clearCacheUrls: suspend (List<String>) -> Unit = ::clearImageCacheUrls
) {
    data class IllustrationSyncProgress(
        val completed: Int,
        val total: Int,
        val currentSongName: String
    )

    data class UpdateOutcome(
        val addedSongNames: List<String>,
        val removedCount: Int
    )

    suspend fun checkUpstreamChanged(): Result<Boolean> = songDataUpdater.checkUpstreamChanged()

    suspend fun update(
        onFileProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        onIllustrationProgress: (IllustrationSyncProgress) -> Unit = {}
    ): Result<UpdateOutcome> {
        val oldSongIds = songDataProvider.getSongs().keys.toSet()
        val result = songDataUpdater.updateAll(onFileProgress)
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull() ?: RuntimeException("unknown"))
        }
        return runCatching {
            reconcileIllustrationCache(
                oldSongIds,
                songDataProvider.getSongs().keys.toSet(),
                onIllustrationProgress
            )
        }
    }

    private suspend fun reconcileIllustrationCache(
        oldSongIds: Set<String>,
        newSongIds: Set<String>,
        onProgress: (IllustrationSyncProgress) -> Unit
    ): UpdateOutcome {
        val added = (newSongIds - oldSongIds).sorted()
        val removed = (oldSongIds - newSongIds).sorted()
        val songNames = songDataProvider.getSongNameMap()
        var failures = 0
        var completed = 0
        val mutex = Mutex()
        coroutineScope {
            val semaphore = Semaphore(6)
            added.map { songId ->
                launch {
                    semaphore.withPermit {
                        val name = songNames[songId] ?: songId
                        mutex.withLock {
                            onProgress(IllustrationSyncProgress(completed, added.size, name))
                        }
                        val result = runCatching {
                            val localUri = artworkFileCache.getOrDownloadThumbnail(songId, illustrationProvider.getLowUrl(songId))
                            thumbnailPreloader.preload(localUri).getOrThrow()
                        }
                        mutex.withLock {
                            completed++
                            if (result.isFailure) failures++
                        }
                    }
                }
            }.forEach { it.join() }
        }
        if (removed.isNotEmpty()) {
            clearCacheUrls(removed.flatMap { listOf(illustrationProvider.getLowUrl(it), illustrationProvider.getStandardUrl(it), illustrationProvider.getBlurUrl(it)) })
            artworkFileCache.clearThumbnails(removed)
            artworkFileCache.clearStandard(removed)
        }
        AppLogger.event("cache", "song_data_illustration_reconcile", mapOf("added" to added.size.toString(), "addedSuccess" to (added.size - failures).toString(), "addedFailure" to failures.toString(), "removed" to removed.size.toString()))
        if (failures > 0) throw IllegalStateException("曲目数据已更新，但部分曲绘未能下载，可稍后在设置中重试")
        return UpdateOutcome(
            addedSongNames = added.map { songNames[it] ?: it },
            removedCount = removed.size
        )
    }
}
