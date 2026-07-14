package org.kasumi321.ushio.phitracker.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.api.GitHubRelease
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.database.RecordEntity
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryDao
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotDao
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.LogFileStore
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.platform.PlatformPaths
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.Summary
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPreloadTest {
    private val dispatcher = StandardTestDispatcher()

    private val testPlatformPaths = PlatformPaths("/tmp/test", "/tmp/test_cache")
    private val testSongDataProvider = SongDataProvider(FakeTextAssetReader, testPlatformPaths)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun preloadAttemptsEveryLowUrlBeforePersistingDone(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showPreloadDialog)

        viewModel.startPreloadIllustrations()
        advanceUntilIdle()

        assertEquals(
            listOf(
                "https://example.test/illLow/song-a.png",
                "https://example.test/illLow/song-b.png"
            ),
            preloader.urls.sorted()
        )
        assertTrue(settings.preloadDone)
        assertTrue(viewModel.uiState.value.illustrationReady)
        assertFalse(viewModel.uiState.value.showPreloadDialog)
        assertEquals(2, viewModel.uiState.value.preloadCompleted)
        assertEquals(1f, viewModel.uiState.value.preloadProgress)
    }

    @Test
    fun failedPreloadDoesNotPersistDoneOrBlockHome(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val preloader = RecordingPreloader(failOnUrl = "https://example.test/illLow/song-b.png")
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        advanceUntilIdle()

        assertFalse(settings.preloadDone)
        assertTrue(viewModel.uiState.value.illustrationReady)
        assertFalse(viewModel.uiState.value.showPreloadDialog)
        assertEquals("部分曲绘图片未能加载", viewModel.uiState.value.error)
        assertEquals(2, viewModel.uiState.value.preloadCompleted)
        assertEquals(1f, viewModel.uiState.value.preloadProgress)
    }

    @Test
    fun getLowIllustrationUrlReturnsIllLowPath(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        val url = viewModel.getLowIllustrationUrl("song-a")
        assertEquals("https://example.test/illLow/song-a.png", url)
    }

    @Test
    fun getStandardIllustrationUrlReturnsIllPath(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        val url = viewModel.getStandardIllustrationUrl("song-a")
        assertEquals("https://example.test/ill/song-a.png", url)
    }

    @Test
    fun clearHighResCacheInvokesCompletionAfterClear(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        var clearedUrls: List<String>? = null
        var completionResult: Result<Unit>? = null
        val viewModel = createViewModel(
            settings, preloader,
            cacheClearFn = { urls -> clearedUrls = urls }
        )
        advanceUntilIdle()

        viewModel.clearHighResCache { result ->
            completionResult = result
        }
        advanceUntilIdle()

        assertTrue((completionResult ?: error("Completion was not called")).isSuccess)
        val urls = clearedUrls ?: error("Cache clear was not called")
        assertEquals(2, urls.size)
        urls.forEach { assertTrue(it.contains("/ill/") && !it.contains("/illLow/"), "Cleared URL must be standard: $it") }
    }

    @Test
    fun cacheB30StandardArtworkDownloadsDistinctStandardArtwork(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache()
        val cachedSave = emptySave().copy(
            gameRecord = mapOf(
                "song-a.0" to SongRecord("song-a.0", mapOf(Difficulty.IN to LevelRecord(990_000, 99f, false))),
                "song-b.0" to SongRecord("song-b.0", mapOf(Difficulty.IN to LevelRecord(980_000, 98f, false)))
            )
        )
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = StatefulRecordDao(emptyList(), emptyList()),
            cachedSave = cachedSave
        )
        val viewModel = createViewModel(
            settingsRepository = settings,
            preloader = preloader,
            artworkFileCache = artworkCache,
            repository = repository
        )
        advanceUntilIdle()

        var completion: Result<Unit>? = null
        viewModel.cacheB30StandardArtwork { result -> completion = result }
        advanceUntilIdle()

        assertTrue((completion ?: error("Completion missing")).isSuccess)
        assertEquals(
            setOf(
                "song-a.0" to "https://example.test/ill/song-a.png",
                "song-b.0" to "https://example.test/ill/song-b.png"
            ),
            artworkCache.downloaded.toSet()
        )
        assertEquals(
            setOf(
                "https://example.test/ill/song-a.png",
                "https://example.test/ill/song-b.png"
            ),
            preloader.urls.toSet(),
            "B30 standard artwork caching also warms display cache with standard URLs"
        )
        assertFalse(viewModel.uiState.value.isCachingB30Artwork)
    }

    @Test
    fun updateSongDataSetsUpdatingStateAndClearsAfterSuccess(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val updater = FakeSongDataUpdater()
        val viewModel = createViewModel(settings, preloader, songDataUpdater = updater)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUpdatingData)
        viewModel.updateSongData()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUpdatingData)
        assertEquals(SongDataUpdater.FILE_NAMES.size, viewModel.uiState.value.updateDataTotal)
        assertEquals(SongDataUpdater.FILE_NAMES.size, viewModel.uiState.value.updateDataProgress)
    }

    @Test
    fun updateSongDataReconcilesAddedAndRemovedIllustrationCaches(): Unit = runTest(dispatcher) {
        val reader = MutableSongDataReader(listOf("song-a", "song-b"))
        val provider = SongDataProvider(reader)
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache()
        var clearedUrls: List<String> = emptyList()
        val updater = FakeSongDataUpdater(
            paths = testPlatformPaths,
            songDataProvider = provider,
            onUpdate = { onProgress ->
                onProgress(SongDataUpdater.FILE_NAMES.size, SongDataUpdater.FILE_NAMES.size, "完成")
                reader.replaceSongs(listOf("song-b", "song-c"))
                provider.invalidateCache()
                Result.success(Unit)
            }
        )
        val viewModel = createViewModel(
            settingsRepository = settings,
            preloader = preloader,
            cacheClearFn = { urls -> clearedUrls = urls },
            artworkFileCache = artworkCache,
            songDataProvider = provider,
            songDataUpdater = updater
        )
        advanceUntilIdle()

        viewModel.updateSongData()
        advanceUntilIdle()

        assertTrue(updater.updateCalled, "Fake updater must be invoked")
        assertEquals(
            listOf("https://example.test/illLow/song-c.png"),
            preloader.urls.filter { it.contains("song-c") },
            "Only the newly added song thumbnail should be preloaded; keys=${provider.getSongs().keys} urls=${preloader.urls} error=${viewModel.uiState.value.updateDataError}"
        )
        assertEquals(
            setOf(
                "https://example.test/illLow/song-a.png",
                "https://example.test/ill/song-a.png",
                "https://example.test/illBlur/song-a.png"
            ),
            clearedUrls.toSet(),
            "Removed songs must clear low, standard and blur Coil cache URLs"
        )
        assertEquals(listOf("song-a.0"), artworkCache.cleared)
        assertEquals(null, viewModel.uiState.value.updateDataError)
    }

    @Test
    fun updateSongDataReportsThumbnailFailureWithoutRollingBackSongData(): Unit = runTest(dispatcher) {
        val reader = MutableSongDataReader(listOf("song-a"))
        val provider = SongDataProvider(reader)
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader(failOnUrl = "https://example.test/illLow/song-b.png")
        val updater = FakeSongDataUpdater(
            paths = testPlatformPaths,
            songDataProvider = provider,
            onUpdate = { onProgress ->
                onProgress(SongDataUpdater.FILE_NAMES.size, SongDataUpdater.FILE_NAMES.size, "完成")
                reader.replaceSongs(listOf("song-a", "song-b"))
                provider.invalidateCache()
                Result.success(Unit)
            }
        )
        val viewModel = createViewModel(
            settingsRepository = settings,
            preloader = preloader,
            songDataProvider = provider,
            songDataUpdater = updater
        )
        advanceUntilIdle()

        viewModel.updateSongData()
        advanceUntilIdle()

        assertTrue(updater.updateCalled, "Fake updater must be invoked")
        assertTrue(provider.getSongs().containsKey("song-b.0"), "Song data update must remain committed")
        assertEquals("曲目数据已更新，但部分曲绘未能下载，可稍后在设置中重试", viewModel.uiState.value.updateDataError)
    }

    @Test
    fun updateSongDataReportsIntermediateFileProgress(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val progressReached = CompletableDeferred<Unit>()
        val continueUpdate = CompletableDeferred<Unit>()
        val updater = FakeSongDataUpdater(
            onUpdate = { onProgress ->
                onProgress(1, SongDataUpdater.FILE_NAMES.size, "difficulty.csv")
                progressReached.complete(Unit)
                continueUpdate.await()
                Result.success(Unit)
            }
        )
        val viewModel = createViewModel(settings, preloader, songDataUpdater = updater)
        advanceUntilIdle()

        viewModel.updateSongData()
        runCurrent()
        progressReached.await()

        assertTrue(viewModel.uiState.value.isUpdatingData)
        assertEquals(1, viewModel.uiState.value.updateDataProgress)
        assertEquals(SongDataUpdater.FILE_NAMES.size, viewModel.uiState.value.updateDataTotal)
        assertEquals("difficulty.csv", viewModel.uiState.value.updateDataFileName)

        continueUpdate.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUpdatingData)
        assertEquals(SongDataUpdater.FILE_NAMES.size, viewModel.uiState.value.updateDataProgress)
        assertEquals("", viewModel.uiState.value.updateDataFileName)
    }

    @Test
    fun dismissUpdateDataErrorClearsError(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        viewModel.dismissUpdateDataError()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.updateDataError)
    }

    @Test
    fun testApiConnectionRequiresPlatformAndId(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        viewModel.testApiConnection()
        advanceUntilIdle()
        assertEquals("请先填写平台名称与平台 ID", viewModel.uiState.value.apiTestMessage)
    }

    @Test
    fun getSyncHistoryReturnsFlowFromDao(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        assertEquals(emptyList(), viewModel.getSyncHistory("song-a").first())
    }

    @Test
    fun recentEffectiveSyncHistoryLoadsAllEntriesFromLatestThreeEffectiveSnapshots(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val snapshots = listOf(
            syncSnapshot(id = 4L, timestamp = 4_000L, dataCount = 2),
            syncSnapshot(id = 3L, timestamp = 3_000L, dataCount = 0),
            syncSnapshot(id = 2L, timestamp = 2_000L, dataCount = 1),
            syncSnapshot(id = 1L, timestamp = 1_000L, dataCount = 1)
        )
        val history = mapOf(
            4L to listOf(
                syncHistory(snapshotId = 4L, songId = "song-a.0", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, timestamp = 4_001L),
                syncHistory(snapshotId = 4L, songId = "song-b.0", difficulty = "HD", score = 940_000, accuracy = 94f, isFullCombo = true, timestamp = 4_002L)
            ),
            3L to emptyList(),
            2L to listOf(syncHistory(snapshotId = 2L, songId = "song-a.0", difficulty = "EZ", score = 900_000, accuracy = 90f, isFullCombo = false, timestamp = 2_001L)),
            1L to listOf(syncHistory(snapshotId = 1L, songId = "song-b.0", difficulty = "IN", score = 910_000, accuracy = 91f, isFullCombo = false, timestamp = 1_001L))
        )
        val viewModel = createViewModel(
            settingsRepository = settings,
            syncSnapshotDao = FakeSyncSnapshotDao(snapshots),
            songSyncHistoryDao = FakeSongSyncHistoryDao(history)
        )
        advanceUntilIdle()

        assertEquals(4_000L, viewModel.uiState.value.lastSyncTime)
        assertEquals(
            listOf("song-a.0", "song-b.0", "song-a.0", "song-b.0"),
            viewModel.uiState.value.recentSyncedRecords.map { it.songId },
            "History should include every entry from the latest three effective snapshots"
        )
        assertEquals("song-a.0", viewModel.uiState.value.lastSyncedRecord?.songId)
        assertEquals(Difficulty.IN, viewModel.uiState.value.lastSyncedRecord?.difficulty)
    }

    @Test
    fun noChangeSyncDoesNotInsertSnapshotOrHistory(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val snapshotDao = TrackingSyncSnapshotDao()
        val historyDao = TrackingSongSyncHistoryDao()
        val existingRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, updatedAt = 1_000L)
        )
        val recordDao = StatefulRecordDao(initialRecords = existingRecords, postSyncRecords = existingRecords)
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = recordDao
        )
        val logFileStore = createTestLogFileStore()

        val viewModel = HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            getSuggestUseCase = GetSuggestUseCase(),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = testSongDataProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settings,
            thumbnailPreloader = preloader,
            clearCacheUrlsFn = {},
            syncSnapshotDao = snapshotDao,
            recordDao = recordDao,
            songSyncHistoryDao = historyDao,
            songDataUpdater = FakeSongDataUpdater(),
            runtimeLogExporter = RuntimeLogExporter(logFileStore),
            crashReportExporter = CrashReportExporter(logFileStore)
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing, "Sync should be complete")
        assertEquals(null, viewModel.uiState.value.error, "Sync should have no error")
        assertNull(snapshotDao.lastInserted, "Snapshot should not be inserted when records are unchanged")
        assertTrue(historyDao.insertedEntries.isEmpty(), "No history should be inserted when records unchanged")
        assertTrue(viewModel.uiState.value.lastSyncTime != null, "lastSyncTime should be updated")
        assertEquals(emptyList(), viewModel.uiState.value.recentSyncedRecords)
        assertNull(viewModel.uiState.value.lastSyncedRecord)
        assertTrue(snapshotDao.getAllOnceCallCount > 0, "getAllOnce should be called for preload/recent sync history")
    }

    @Test
    fun changedSyncInsertsSnapshotAndHistoryWithBeta4Metadata(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val snapshotDao = TrackingSyncSnapshotDao()
        val historyDao = TrackingSongSyncHistoryDao()
        val initialRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 900_000, accuracy = 90f, isFullCombo = false, updatedAt = 1_000L)
        )
        val postSyncRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = true, updatedAt = 2_000L)
        )
        val recordDao = StatefulRecordDao(initialRecords = initialRecords, postSyncRecords = postSyncRecords)
        val cachedSave = saveWithRecord(
            songId = "song-a.0",
            difficulty = Difficulty.IN,
            score = 990_000,
            accuracy = 99f,
            isFullCombo = false
        )
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = recordDao,
            cachedSave = cachedSave
        )
        val logFileStore = createTestLogFileStore()

        val viewModel = HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            getSuggestUseCase = GetSuggestUseCase(),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = testSongDataProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settings,
            thumbnailPreloader = preloader,
            clearCacheUrlsFn = {},
            syncSnapshotDao = snapshotDao,
            recordDao = recordDao,
            songSyncHistoryDao = historyDao,
            songDataUpdater = FakeSongDataUpdater(),
            runtimeLogExporter = RuntimeLogExporter(logFileStore),
            crashReportExporter = CrashReportExporter(logFileStore)
        )
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSyncing, "Sync should be complete")
        assertEquals(null, viewModel.uiState.value.error, "Sync should have no error")
        // Snapshot must be inserted
        assertNotNull(snapshotDao.lastInserted, "Snapshot should be inserted on changed sync")
        assertEquals(viewModel.uiState.value.displayRks, snapshotDao.lastInserted!!.rks, "Snapshot rks should come from UI state")
        // History rows must be inserted
        assertTrue(historyDao.insertedEntries.isNotEmpty(), "History should be inserted when records changed")
        assertEquals(1, historyDao.insertedEntries.size, "One changed record = one history entry")
        val history = historyDao.insertedEntries.first()
        assertEquals("song-a", history.songId)
        assertEquals(950_000, history.score)
        assertEquals(95f, history.accuracy)
        assertTrue(history.isFullCombo)
        val expectedB30Top = assertNotNull(viewModel.uiState.value.b30.firstOrNull(), "Test must prove non-null B30 metadata")
        assertEquals("song-a.0", expectedB30Top.songId)
        assertEquals(990_000, expectedB30Top.score)
        assertEquals(expectedB30Top.songId, snapshotDao.lastInserted!!.lastSyncedSongId)
        assertEquals(expectedB30Top.difficulty.name, snapshotDao.lastInserted!!.lastSyncedDifficulty)
        assertEquals(expectedB30Top.score, snapshotDao.lastInserted!!.lastSyncedScore)
        assertEquals(expectedB30Top.accuracy, snapshotDao.lastInserted!!.lastSyncedAccuracy)
        assertEquals(1, snapshotDao.lastInserted!!.dataCount)
        assertTrue(snapshotDao.getAllOnceCallCount > 0, "getAllOnce should be called for preload/recent sync history")
    }

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository,
        preloader: IllustrationThumbnailPreloader = RecordingPreloader(),
        cacheClearFn: suspend (List<String>) -> Unit = {},
        artworkFileCache: StandardArtworkCache = RecordingStandardArtworkCache(),
        songDataProvider: SongDataProvider = testSongDataProvider,
        syncSnapshotDao: SyncSnapshotDao = FakeSyncSnapshotDao(),
        recordDao: RecordDao = FakeRecordDao(),
        songSyncHistoryDao: SongSyncHistoryDao = FakeSongSyncHistoryDao(),
        songDataUpdater: FakeSongDataUpdater = FakeSongDataUpdater(
            paths = testPlatformPaths,
            songDataProvider = songDataProvider
        ),
        appVersionName: String = "",
        repository: PhigrosRepository = FakePhigrosRepository()
    ): HomeViewModel {
        val illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
        val logFileStore = createTestLogFileStore()
        return HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            getSuggestUseCase = GetSuggestUseCase(),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = songDataProvider,
            illustrationProvider = illustrationProvider,
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settingsRepository,
            artworkFileCache = artworkFileCache,
            thumbnailPreloader = preloader,
            clearCacheUrlsFn = cacheClearFn,
            syncSnapshotDao = syncSnapshotDao,
            recordDao = recordDao,
            songSyncHistoryDao = songSyncHistoryDao,
            songDataUpdater = songDataUpdater,
            runtimeLogExporter = RuntimeLogExporter(logFileStore),
            crashReportExporter = CrashReportExporter(logFileStore),
            appVersionNameProvider = { appVersionName }
        )
    }

    private class RecordingPreloader(
        private val failOnUrl: String? = null
    ) : IllustrationThumbnailPreloader {
        val urls: MutableList<String> = mutableListOf()

        override suspend fun preload(url: String): Result<Unit> {
            urls += url
            if (url == failOnUrl) return Result.failure(IllegalStateException("preload failed"))
            return Result.success(Unit)
        }
    }

    private class RecordingStandardArtworkCache : StandardArtworkCache {
        val downloaded: MutableList<Pair<String, String>> = mutableListOf()
        val cleared: MutableList<String> = mutableListOf()
        var clearAllCalled: Boolean = false

        override suspend fun getOrDownloadStandard(songId: String, url: String): String {
            downloaded += songId to url
            return "/cache/standard/$songId.png"
        }

        override fun getStandardIfPresent(songId: String): String? = null

        override fun clearStandard(songIds: Iterable<String>) {
            cleared += songIds
        }

        override fun clearAllStandard() {
            clearAllCalled = true
        }
    }

    private class FakeSettingsRepository(
        preloadDone: Boolean,
        autoCheckUpdate: Boolean = true,
        includePreRelease: Boolean = false
    ) : SettingsRepository {
        override val themeMode: Flow<Int> = flowOf(0)
        override val themeColorSource: Flow<String> = flowOf("system")
        override val seedColorArgb: Flow<Int> = flowOf(-10011977)
        override val themeImageSeedColorArgb: Flow<Int?> = flowOf(null)
        override val themeImageUri: Flow<String?> = flowOf(null)
        override val paletteStyleName: Flow<String> = flowOf("TonalSpot")
        override val showB30Overflow: Flow<Boolean> = flowOf(false)
        override val overflowCount: Flow<Int> = flowOf(9)
        var preloadDone = preloadDone
            private set

        override suspend fun setThemeMode(mode: Int) = Unit
        override suspend fun setThemeColorSource(source: String) = Unit
        override suspend fun setSeedColorArgb(argb: Int) = Unit
        override suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int) = Unit
        override suspend fun clearThemeImageColor() = Unit
        override suspend fun setPaletteStyleName(name: String) = Unit
        override suspend fun setShowB30Overflow(show: Boolean) = Unit
        override suspend fun setOverflowCount(count: Int) = Unit
        override suspend fun getPreloadDone(): Boolean = preloadDone
        override suspend fun setPreloadDone(done: Boolean) {
            preloadDone = done
        }

        override val avatarUri: Flow<String?> = flowOf(null)
        override suspend fun setAvatarUri(uri: String?) = Unit
        override val moneyString: Flow<String> = flowOf("")
        override suspend fun setMoneyString(money: String) = Unit
        private val _includePreRelease = MutableStateFlow(includePreRelease)
        override val includePreRelease: Flow<Boolean> = _includePreRelease.asStateFlow()
        private val _autoCheckUpdate = MutableStateFlow(autoCheckUpdate)
        override val autoCheckUpdate: Flow<Boolean> = _autoCheckUpdate.asStateFlow()
        var autoCheckUpdateSetValue: Boolean? = null
            private set
        override suspend fun setIncludePreRelease(enabled: Boolean) {
            _includePreRelease.value = enabled
        }
        override suspend fun setAutoCheckUpdate(enabled: Boolean) {
            autoCheckUpdateSetValue = enabled
            _autoCheckUpdate.value = enabled
        }
        override val apiEnabled: Flow<Boolean> = flowOf(false)
        override suspend fun setApiEnabled(enabled: Boolean) = Unit
        override val useApiData: Flow<Boolean> = flowOf(false)
        override suspend fun setUseApiData(useApiData: Boolean) = Unit
        override val apiId: Flow<String> = flowOf("")
        override suspend fun setApiId(apiId: String) = Unit
        override val apiPlatform: Flow<String> = flowOf("")
        override suspend fun setApiPlatform(platform: String) = Unit
        override val apiPlatformId: Flow<String> = flowOf("")
        override suspend fun setApiPlatformId(platformId: String) = Unit
        override val crashNotificationGuideShown: Flow<Boolean> = flowOf(false)
        override suspend fun setCrashNotificationGuideShown(shown: Boolean) = Unit
    }

    private open class FakePhigrosRepository : PhigrosRepository {
        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> {
            error("Not needed for this test")
        }

        override suspend fun syncSave(sessionToken: String, server: Server): Result<Save> {
            error("Not needed for this test")
        }

        override fun getCachedSave(): Flow<Save?> = MutableStateFlow(emptySave())
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) = Unit
        override suspend fun getSessionToken(): Pair<String, Server>? = Pair("fake-token", Server.CN)
        override suspend fun clearData() = Unit
        override fun clearTokenSync() = Unit

        override suspend fun apiTest(): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiBind(platform: String, platformId: String, token: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetSingleSave(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetSave(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetSaveInfo(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetRank(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetAvgAcc(songId: String, difficulty: String, minRks: Float?, maxRks: Float?): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetAllAvgAcc(songIds: List<String>): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetApFcTotal(songId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetRksStats(): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetSaveHistory(platform: String, platformId: String, request: List<String>): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetScoreHistory(platform: String, platformId: String, songId: String?, difficulty: String?): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetRankByUser(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))
        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        open var fetchLatestReleaseCallCount = 0
        val fetchLatestReleaseIncludePreReleaseValues = mutableListOf<Boolean>()
        open var fetchLatestReleaseResult: Result<GitHubRelease> =
            Result.failure(IllegalStateException("Not configured"))

        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<GitHubRelease> {
            fetchLatestReleaseCallCount++
            fetchLatestReleaseIncludePreReleaseValues.add(includePreRelease)
            return fetchLatestReleaseResult
        }
    }

    private class FakeSyncSnapshotDao(
        private val snapshots: List<SyncSnapshotEntity> = emptyList()
    ) : SyncSnapshotDao {
        override suspend fun insert(snapshot: SyncSnapshotEntity) = Unit
        override suspend fun insertAndGetId(snapshot: SyncSnapshotEntity): Long = 1L
        override fun getAll(): Flow<List<SyncSnapshotEntity>> = flowOf(snapshots)
        override suspend fun getAllOnce(): List<SyncSnapshotEntity> = snapshots
        override suspend fun getLatest(): SyncSnapshotEntity? = snapshots.firstOrNull()
    }

    private class FakeRecordDao : RecordDao {
        override suspend fun insertAll(records: List<RecordEntity>) = Unit
        override fun getAllRecords(): Flow<List<RecordEntity>> = flowOf(emptyList())
        override suspend fun getAllRecordsOnce(): List<RecordEntity> = emptyList()
        override suspend fun getRecordsBySong(songId: String): List<RecordEntity> = emptyList()
        override suspend fun deleteAll() = Unit
        override suspend fun getRecordCount(): Int = 0
        override suspend fun getDistinctSongCount(): Int = 0
        override suspend fun getClearCountByDifficulty(difficulty: String): Int = 0
        override suspend fun getTotalFcCount(): Int = 0
        override suspend fun getTotalPhiCount(): Int = 0
    }

    private class FakeSongSyncHistoryDao(
        private val bySnapshotId: Map<Long, List<SongSyncHistoryEntity>> = emptyMap()
    ) : SongSyncHistoryDao {
        override suspend fun insertAll(entries: List<SongSyncHistoryEntity>) = Unit
        override fun getBySongId(songId: String): Flow<List<SongSyncHistoryEntity>> = flowOf(emptyList())
        override suspend fun getRecentBySongId(songId: String, limit: Int): List<SongSyncHistoryEntity> = emptyList()
        override suspend fun getBySnapshotId(snapshotId: Long): List<SongSyncHistoryEntity> = bySnapshotId[snapshotId].orEmpty()
        override suspend fun getRecent(limit: Int): List<SongSyncHistoryEntity> = bySnapshotId.values.flatten().take(limit)
    }

    private class FakeSongDataUpdater(
        httpClient: io.ktor.client.HttpClient = io.ktor.client.HttpClient(),
        paths: PlatformPaths = PlatformPaths("/tmp/test", "/tmp/test_cache"),
        songDataProvider: SongDataProvider = SongDataProvider(FakeTextAssetReader, PlatformPaths("/tmp/test", "/tmp/test_cache")),
        private val onUpdate: suspend ((Int, Int, String) -> Unit) -> Result<Unit> = { onProgress ->
            onProgress(SongDataUpdater.FILE_NAMES.size, SongDataUpdater.FILE_NAMES.size, "完成")
            Result.success(Unit)
        }
    ) : SongDataUpdater(
        httpClient = httpClient,
        paths = paths,
        songDataProvider = songDataProvider
    ) {
        var updateCalled = false
            private set

        override suspend fun updateAll(onProgress: (Int, Int, String) -> Unit): Result<Unit> {
            updateCalled = true
            return onUpdate(onProgress)
        }
    }

    private class TrackingSyncSnapshotDao : SyncSnapshotDao {
        var lastInserted: SyncSnapshotEntity? = null
            private set
        var getAllOnceCallCount: Int = 0
            private set

        override suspend fun insert(snapshot: SyncSnapshotEntity) {
            lastInserted = snapshot
        }

        override suspend fun insertAndGetId(snapshot: SyncSnapshotEntity): Long {
            lastInserted = snapshot
            return 1L
        }

        override fun getAll(): Flow<List<SyncSnapshotEntity>> = flowOf(emptyList())
        override suspend fun getAllOnce(): List<SyncSnapshotEntity> {
            getAllOnceCallCount++
            return emptyList()
        }
        override suspend fun getLatest(): SyncSnapshotEntity? = null
    }

    private class TrackingSongSyncHistoryDao : SongSyncHistoryDao {
        val insertedEntries: MutableList<SongSyncHistoryEntity> = mutableListOf()

        override suspend fun insertAll(entries: List<SongSyncHistoryEntity>) {
            insertedEntries.addAll(entries)
        }

        override fun getBySongId(songId: String): Flow<List<SongSyncHistoryEntity>> = flowOf(emptyList())
        override suspend fun getRecentBySongId(songId: String, limit: Int): List<SongSyncHistoryEntity> = emptyList()
        override suspend fun getBySnapshotId(snapshotId: Long): List<SongSyncHistoryEntity> = emptyList()
        override suspend fun getRecent(limit: Int): List<SongSyncHistoryEntity> = emptyList()
    }

    private class StatefulRecordDao(
        initialRecords: List<RecordEntity>,
        private val postSyncRecords: List<RecordEntity>
    ) : RecordDao {
        private var callCount = 0
        private var currentRecords = initialRecords.toMutableList()

        override suspend fun insertAll(records: List<RecordEntity>) {
            currentRecords.clear()
            currentRecords.addAll(records)
        }

        override fun getAllRecords(): Flow<List<RecordEntity>> = flowOf(currentRecords.toList())
        override suspend fun getAllRecordsOnce(): List<RecordEntity> {
            val records = if (callCount == 0) currentRecords.toList() else postSyncRecords.toList()
            callCount++
            return records
        }

        override suspend fun getRecordsBySong(songId: String): List<RecordEntity> = currentRecords.filter { it.songId == songId }
        override suspend fun deleteAll() { currentRecords.clear() }
        override suspend fun getRecordCount(): Int = currentRecords.size
        override suspend fun getDistinctSongCount(): Int = currentRecords.map { it.songId }.distinct().size
        override suspend fun getClearCountByDifficulty(difficulty: String): Int = 0
        override suspend fun getTotalFcCount(): Int = currentRecords.count { it.isFullCombo }
        override suspend fun getTotalPhiCount(): Int = currentRecords.count { it.accuracy >= 100f }
    }

    private class FakePhigrosRepositoryForSync(
        private val syncResult: Result<Save>,
        private val recordDao: StatefulRecordDao,
        private val cachedSave: Save? = emptySave()
    ) : FakePhigrosRepository() {
        override fun getCachedSave(): Flow<Save?> = flowOf(cachedSave)

        override suspend fun syncSave(sessionToken: String, server: Server): Result<Save> {
            return syncResult
        }
    }

    private object FakeTextAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "difficulty.csv" -> "songId,EZ,HD,IN,AT\nsong-a,1.0,2.0,3.0,4.0\nsong-b,1.0,2.0,3.0,4.0"
            "info.csv" -> "songId,name,composer,illustrator,EZCharter,HDCharter,INCharter,ATCharter\nsong-a,Song A,Composer,Illustrator,,,,\nsong-b,Song B,Composer,Illustrator,,,,"
            "infolist.json" -> "{}"
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    private class MutableSongDataReader(
        private var songIds: List<String>
    ) : TextAssetReader {
        fun replaceSongs(newSongIds: List<String>) {
            songIds = newSongIds
        }

        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "difficulty.csv" -> buildString {
                appendLine("songId,EZ,HD,IN,AT")
                songIds.forEach { songId -> appendLine("$songId,1.0,2.0,3.0,4.0") }
            }.trimEnd()
            "info.csv" -> buildString {
                appendLine("songId,name,composer,illustrator,EZCharter,HDCharter,INCharter,ATCharter")
                songIds.forEach { songId -> appendLine("$songId,Song $songId,Composer,Illustrator,,,,") }
            }.trimEnd()
            "infolist.json" -> "{}"
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    // --- Multi-chapter filter tests (Phase F) ---

    @Test
    fun multiChapterFilterStacking(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Initially no chapter filter — all 3 songs visible
        assertEquals(3, viewModel.uiState.value.filteredSongs.size,
            "All songs should be visible with no chapter filter")

        // Toggle "Single" — songs in "Single" chapter: song-a.0 and song-c.0
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredSongs.size,
            "2 songs in Single chapter")
        assertTrue(viewModel.uiState.value.filteredSongs.all { it.chapter == "Single" })

        // Toggle also "Collection" — now both chapters active
        viewModel.toggleChapter("Collection")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filteredSongs.size,
            "Stacking both chapters shows all 3 songs")
        assertEquals(setOf("Single", "Collection"), viewModel.uiState.value.selectedChapters)
    }

    @Test
    fun toggleChapterOff(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Toggle both chapters
        viewModel.toggleChapter("Single")
        viewModel.toggleChapter("Collection")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filteredSongs.size,
            "Both chapters active")
        assertEquals(setOf("Single", "Collection"), viewModel.uiState.value.selectedChapters)

        // Toggle off "Single" — only "Collection" remains
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredSongs.size,
            "Only Collection chapter songs after toggling Single off")
        assertTrue(viewModel.uiState.value.filteredSongs.all { it.chapter == "Collection" })
        assertEquals(setOf("Collection"), viewModel.uiState.value.selectedChapters)
    }

    @Test
    fun clearChaptersRestoresAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply chapter filter
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredSongs.size,
            "Filtered to Single chapter")
        assertTrue(viewModel.uiState.value.selectedChapters.isNotEmpty())

        // Clear chapters
        viewModel.clearChapters()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.filteredSongs.size,
            "All songs restored after clearing chapters")
        assertTrue(viewModel.uiState.value.selectedChapters.isEmpty(),
            "selectedChapters should be empty after clear")
    }

    @Test
    fun resetFiltersRestoresAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply multiple filters: chapter, difficulty, level range
        viewModel.toggleChapter("Single")
        viewModel.filterByDifficulty(Difficulty.HD)
        viewModel.filterByLevelRange(3, 10)
        advanceUntilIdle()

        // Verify filtering is active
        assertTrue(viewModel.uiState.value.selectedChapters.isNotEmpty())
        assertNotNull(viewModel.uiState.value.selectedDifficulty)

        // Reset
        viewModel.resetFilters()
        advanceUntilIdle()

        // All filters cleared
        assertTrue(viewModel.uiState.value.selectedChapters.isEmpty())
        assertEquals(null, viewModel.uiState.value.selectedDifficulty)
        assertEquals(1, viewModel.uiState.value.minLevel)
        assertEquals(17, viewModel.uiState.value.maxLevel)
        assertEquals(3, viewModel.uiState.value.filteredSongs.size,
            "Reset should restore all songs")
    }

    @Test
    fun emptyChaptersMatchesAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // selectedChapters starts empty — all songs match
        assertTrue(viewModel.uiState.value.selectedChapters.isEmpty())
        assertEquals(3, viewModel.uiState.value.filteredSongs.size)

        // Filter by difficulty only — chapters still empty, difficulty narrows
        viewModel.filterByDifficulty(Difficulty.IN)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedChapters.isEmpty())
        // All 3 songs have IN=3.0, min=1 max=16 → all should match
        assertEquals(3, viewModel.uiState.value.filteredSongs.size)
    }

    @Test
    fun chapterFilterStacksWithSearch(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply chapter filter first
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredSongs.size)

        // Add search query that narrows further
        viewModel.searchSongs("Song A")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredSongs.size,
            "Search + chapter filter should stack")
        assertEquals("song-a.0", viewModel.uiState.value.filteredSongs.first().id)
    }

    @Test
    fun apiToolResultPreservesRows() {
        val rows = listOf(
            ApiToolRow("标签 A", "值 A"),
            ApiToolRow("标签 B", "值 B")
        )
        val result = ApiToolResult(message = "ok", rows = rows)
        assertEquals(2, result.rows.size)
        assertEquals("标签 A", result.rows[0].label)
        assertEquals("值 A", result.rows[0].value)
        assertEquals("ok", result.message)
    }

    // ---- Phase C: suggestion tests ----

    @Test
    fun emptyB30YieldsEmptySuggestions(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        // No B30 records → empty suggestions
        assertTrue(viewModel.uiState.value.suggestItems.isEmpty(),
            "Empty B30 should yield empty suggestions")
    }

    @Test
    fun noCachedSaveYieldsEmptySuggestions(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val snapshotDao = TrackingSyncSnapshotDao()
        val historyDao = TrackingSongSyncHistoryDao()
        val existingRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, updatedAt = 1_000L)
        )
        val recordDao = StatefulRecordDao(initialRecords = existingRecords, postSyncRecords = existingRecords)
        // No cached save
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = recordDao,
            cachedSave = null
        )
        val logFileStore = createTestLogFileStore()

        val viewModel = HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            getSuggestUseCase = GetSuggestUseCase(),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = testSongDataProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settings,
            thumbnailPreloader = preloader,
            clearCacheUrlsFn = {},
            syncSnapshotDao = snapshotDao,
            recordDao = recordDao,
            songSyncHistoryDao = historyDao,
            songDataUpdater = FakeSongDataUpdater(),
            runtimeLogExporter = RuntimeLogExporter(logFileStore),
            crashReportExporter = CrashReportExporter(logFileStore)
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.suggestItems.isEmpty(),
            "No cached save should yield empty suggestions")
    }

    @Test
    fun insufficientB30YieldsEmptySuggestions(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        // Only 1 record — below the 20 minimum
        val existingRecords = listOf(
            RecordEntity(songId = "song-a", difficulty = "IN", score = 950_000, accuracy = 95f, isFullCombo = false, updatedAt = 1_000L)
        )
        val recordDao = StatefulRecordDao(initialRecords = existingRecords, postSyncRecords = existingRecords)
        val cachedSave = saveWithRecord(
            songId = "song-a.0",
            difficulty = Difficulty.IN,
            score = 990_000,
            accuracy = 99f,
            isFullCombo = false
        )
        val repository = FakePhigrosRepositoryForSync(
            syncResult = Result.success(saveWithRks(15.5f)),
            recordDao = recordDao,
            cachedSave = cachedSave
        )
        val logFileStore = createTestLogFileStore()

        val viewModel = HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            getSuggestUseCase = GetSuggestUseCase(),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = testSongDataProvider,
            illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") },
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settings,
            thumbnailPreloader = preloader,
            clearCacheUrlsFn = {},
            syncSnapshotDao = FakeSyncSnapshotDao(),
            recordDao = recordDao,
            songSyncHistoryDao = FakeSongSyncHistoryDao(),
            songDataUpdater = FakeSongDataUpdater(),
            runtimeLogExporter = RuntimeLogExporter(logFileStore),
            crashReportExporter = CrashReportExporter(logFileStore)
        )
        advanceUntilIdle()

        // B30 has only 1 record (< 20 minimum) → empty suggestions
        assertTrue(viewModel.uiState.value.suggestItems.isEmpty(),
            "B30 with < 20 records should yield empty suggestions")
    }

    @Test
    fun getSuggestUseCaseProducesCorrectItems(): Unit = runTest(dispatcher) {
        val useCase = GetSuggestUseCase()
        val diffMap = mapOf(
            "song-a" to mapOf(
                Difficulty.EZ to 1.0f,
                Difficulty.HD to 5.0f,
                Difficulty.IN to 10.0f,
                Difficulty.AT to 14.0f
            ),
            "song-b" to mapOf(
                Difficulty.EZ to 2.0f,
                Difficulty.HD to 6.0f,
                Difficulty.IN to 11.0f,
                Difficulty.AT to 15.0f
            )
        )
        val nameMap = mapOf("song-a" to "Song A", "song-b" to "Song B")

        // Build 20 B30 records — minimum for beta5 threshold (index 19)
        val b30 = (1..20).map { i ->
            org.kasumi321.ushio.phitracker.domain.model.BestRecord(
                songId = "song-$i",
                songName = "Song $i",
                difficulty = Difficulty.IN,
                score = 900_000 + i * 1000,
                accuracy = 90f + i * 0.3f,
                isFullCombo = false,
                chartConstant = 10.0f,
                rks = 6.0f + i * 0.1f,
                isPhi = false
            )
        }
        // threshold = b30[19].rks = 6.0 + 20*0.1 = 8.0

        // Records: song-a IN has acc 85 (below threshold), song-b IN has acc 95 (above)
        val records = mapOf(
            "song-a" to SongRecord(
                songId = "song-a",
                levels = mapOf(Difficulty.IN to LevelRecord(850_000, 85f, false))
            ),
            "song-b" to SongRecord(
                songId = "song-b",
                levels = mapOf(Difficulty.IN to LevelRecord(950_000, 95f, true))
            )
        )

        val suggestions = useCase(b30, records, diffMap, nameMap, limit = 30)

        // song-a IN should be suggested (acc 85, cc 10: rks = 4.44 < 8.0 threshold)
        val songaSuggest = suggestions.find { it.songId == "song-a" && it.difficulty == Difficulty.IN }
        assertNotNull(songaSuggest, "song-a IN should be suggested")
        assertEquals("Song A", songaSuggest.songName)
        assertEquals(85f, songaSuggest.currentAcc)
        assertTrue(songaSuggest.targetAcc > 85f)
        assertEquals(10.0f, songaSuggest.chartConstant)
        assertFalse(songaSuggest.isFullCombo)

        // song-b IN has currentRks = ((95-55)/45)^2 * 11 = 8.69 >= threshold 8.0 → NOT suggested
        assertNull(suggestions.find { it.songId == "song-b" && it.difficulty == Difficulty.IN },
            "song-b IN should NOT be suggested (currentRks >= threshold)")
    }

    @Test
    fun suggestTargetInputKeepsIllegalTextAndReportsExplicitError(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createViewModel(settingsRepository = settings)
        advanceUntilIdle()

        viewModel.setSuggestTargetInput("16.123")
        advanceUntilIdle()

        assertEquals("16.123", viewModel.uiState.value.suggestTargetInput)
        assertEquals(
            "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数",
            viewModel.uiState.value.suggestTargetError
        )

        viewModel.setSuggestTargetInput("abc")
        advanceUntilIdle()

        assertEquals("abc", viewModel.uiState.value.suggestTargetInput)
        assertEquals(
            "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数",
            viewModel.uiState.value.suggestTargetError
        )
    }

    private fun createChapterFilterViewModel(
        settingsRepository: FakeSettingsRepository
    ): HomeViewModel {
        val songDataProvider = SongDataProvider(ChapterTestAssetReader, testPlatformPaths)
        val repository = FakePhigrosRepository()
        val illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
        val logFileStore = createTestLogFileStore()
        return HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            getSuggestUseCase = GetSuggestUseCase(),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = songDataProvider,
            illustrationProvider = illustrationProvider,
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settingsRepository,
            thumbnailPreloader = RecordingPreloader(),
            clearCacheUrlsFn = {},
            syncSnapshotDao = FakeSyncSnapshotDao(),
            recordDao = FakeRecordDao(),
            songSyncHistoryDao = FakeSongSyncHistoryDao(),
            songDataUpdater = FakeSongDataUpdater(
                paths = testPlatformPaths,
                songDataProvider = songDataProvider
            ),
            runtimeLogExporter = RuntimeLogExporter(logFileStore),
            crashReportExporter = CrashReportExporter(logFileStore)
        )
    }

    private object ChapterTestAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "difficulty.csv" -> "songId,EZ,HD,IN,AT\nsong-a,1.0,2.0,3.0,4.0\nsong-b,1.0,2.0,3.0,4.0\nsong-c,1.0,2.0,3.0,4.0"
            "info.csv" -> "songId,name,composer,illustrator,EZCharter,HDCharter,INCharter,ATCharter\nsong-a,Song A,Composer,Illus,,,,\nsong-b,Song B,Composer,Illus,,,,\nsong-c,Song C,Composer,Illus,,,,"
            "infolist.json" -> """{"song-a":{"chapter":"Single"},"song-b":{"chapter":"Collection"},"song-c":{"chapter":"Single"}}"""
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    // ---- Phase G: auto-update core tests ----

    @Test
    fun autoCheckUpdateDefaultsTrueInUiState(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.autoCheckUpdate,
            "autoCheckUpdate should default true")
    }

    @Test
    fun setAutoCheckUpdateFalsePersistsAndReflectsInState(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        viewModel.setAutoCheckUpdate(false)
        advanceUntilIdle()

        assertEquals(false, settings.autoCheckUpdateSetValue,
            "Repository setAutoCheckUpdate should be called with false")
        assertEquals(false, viewModel.uiState.value.autoCheckUpdate,
            "UI state should reflect autoCheckUpdate=false")
    }

    @Test
    fun startupAutoCheckEnabledFetchesAndSetsAvailable(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = true)
        val repository = FakePhigrosRepository().apply {
            fetchLatestReleaseResult = Result.success(
                GitHubRelease(
                    tagName = "v9.9.9",
                    htmlUrl = "https://example.test/release/v9.9.9",
                    prerelease = false,
                    body = "New version available"
                )
            )
        }
        val viewModel = createViewModel(
            settings, RecordingPreloader(),
            appVersionName = "0.1.0",
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(1, repository.fetchLatestReleaseCallCount,
            "Startup auto-check should call fetchLatestRelease exactly once")
        assertEquals(
            listOf(false),
            repository.fetchLatestReleaseIncludePreReleaseValues,
            "Startup auto-check should use includePreRelease=false by default"
        )
        val state = viewModel.uiState.value.updateCheckState
        assertTrue(state is UpdateCheckState.Available,
            "Should be Available when a newer version is returned")
        val available = state as UpdateCheckState.Available
        assertEquals("v9.9.9", available.version)
        assertEquals("https://example.test/release/v9.9.9", available.htmlUrl)
        assertEquals("New version available", available.body)
    }

    @Test
    fun startupAutoCheckDisabledLeavesIdleAndSkipsFetch(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository()
        val viewModel = createViewModel(
            settings, RecordingPreloader(),
            appVersionName = "0.1.0",
            repository = repository
        )
        advanceUntilIdle()

        assertEquals(0, repository.fetchLatestReleaseCallCount,
            "Should NOT call fetchLatestRelease when auto-check is disabled")
        assertTrue(viewModel.uiState.value.updateCheckState is UpdateCheckState.Idle,
            "UpdateCheckState should remain Idle")
    }

    @Test
    fun manualCheckRespectsIncludePreRelease(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository().apply {
            fetchLatestReleaseResult = Result.success(
                GitHubRelease(
                    tagName = "v2.0.0-beta1",
                    htmlUrl = "https://example.test/release/v2.0.0-beta1",
                    prerelease = true,
                    body = "Beta release"
                )
            )
        }
        val viewModel = createViewModel(
            settings, RecordingPreloader(),
            appVersionName = "0.1.0",
            repository = repository
        )
        advanceUntilIdle()

        // Enable includePreRelease via settings setter
        viewModel.setIncludePreRelease(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.includePreRelease,
            "includePreRelease should be true after setting")

        // Manual check should pass the current includePreRelease
        viewModel.checkForUpdate("0.1.0")
        advanceUntilIdle()

        assertEquals(1, repository.fetchLatestReleaseCallCount,
            "Manual check should call fetchLatestRelease")
        assertEquals(
            listOf(true),
            repository.fetchLatestReleaseIncludePreReleaseValues,
            "Manual check should pass includePreRelease=true"
        )
        val state = viewModel.uiState.value.updateCheckState
        assertTrue(state is UpdateCheckState.Available,
            "Should find the beta release available")
    }

    private companion object {
        fun createTestLogFileStore(): LogFileStore {
            val testDir = "/tmp/phi_tracker_test_${kotlin.random.Random.nextInt()}"
            return LogFileStore(
                fileSystem = FileSystem.SYSTEM,
                runtimeLogDir = "$testDir/runtime_logs".toPath(),
                crashLogDir = "$testDir/crash_logs".toPath()
            )
        }

        fun emptySave(): Save = Save(
            gameRecord = emptyMap(),
            gameProgress = GameProgress(
                isFirstRun = false,
                legacyChapterFinished = false,
                alreadyShowCollectionTip = false,
                alreadyShowAutoUnlockINTip = false,
                completed = "",
                songUpdateInfo = 0,
                challengeModeRank = 0,
                money = emptyList(),
                unlockFlagOfSpasmodic = 0,
                unlockFlagOfIgallta = 0,
                unlockFlagOfRrharil = 0,
                flagOfSongRecordKey = 0,
                randomVersionUnlocked = null,
                chapter8UnlockBegin = null,
                chapter8UnlockSecondPhase = null,
                chapter8Passed = null,
                chapter8SongUnlocked = null
            ),
            user = UserSettings(showPlayerId = false, selfIntro = "", avatar = "", background = ""),
            summary = null
        )

        fun saveWithRks(rks: Float): Save = Save(
            gameRecord = emptyMap(),
            gameProgress = GameProgress(
                isFirstRun = false, legacyChapterFinished = false,
                alreadyShowCollectionTip = false, alreadyShowAutoUnlockINTip = false,
                completed = "", songUpdateInfo = 0, challengeModeRank = 0,
                money = emptyList(), unlockFlagOfSpasmodic = 0,
                unlockFlagOfIgallta = 0, unlockFlagOfRrharil = 0,
                flagOfSongRecordKey = 0, randomVersionUnlocked = null,
                chapter8UnlockBegin = null, chapter8UnlockSecondPhase = null,
                chapter8Passed = null, chapter8SongUnlocked = null
            ),
            user = UserSettings(showPlayerId = false, selfIntro = "", avatar = "", background = ""),
            summary = Summary(
                saveVersion = 1, challengeModeRank = 0,
                rks = rks, gameVersion = 1, avatar = "",
                progress = emptyList()
            )
        )

        fun saveWithRecord(
            songId: String,
            difficulty: Difficulty,
            score: Int,
            accuracy: Float,
            isFullCombo: Boolean
        ): Save = emptySave().copy(
            gameRecord = mapOf(
                songId to SongRecord(
                    songId = songId,
                    levels = mapOf(difficulty to LevelRecord(score, accuracy, isFullCombo))
                )
            )
        )

        fun syncSnapshot(
            id: Long,
            timestamp: Long,
            dataCount: Int,
            rks: Float = 15f,
            nickname: String = "N"
        ): SyncSnapshotEntity = SyncSnapshotEntity(
            id = id,
            timestamp = timestamp,
            rks = rks,
            nickname = nickname,
            dataCount = dataCount,
            lastSyncedSongId = null,
            lastSyncedDifficulty = null,
            lastSyncedScore = null,
            lastSyncedAccuracy = null
        )

        fun syncHistory(
            snapshotId: Long,
            songId: String,
            difficulty: String,
            score: Int,
            accuracy: Float,
            isFullCombo: Boolean,
            timestamp: Long
        ): SongSyncHistoryEntity = SongSyncHistoryEntity(
            snapshotId = snapshotId,
            songId = songId,
            difficulty = difficulty,
            score = score,
            accuracy = accuracy,
            isFullCombo = isFullCombo,
            timestamp = timestamp
        )
    }
}
