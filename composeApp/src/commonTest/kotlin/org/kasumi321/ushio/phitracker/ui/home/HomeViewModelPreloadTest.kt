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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.database.RecordEntity
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
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.Summary
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
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
    private val viewModelLifecycle = ViewModelTestLifecycle()

    private val testPlatformPaths = PlatformPaths("/tmp/test", "/tmp/test_cache")
    private val testSongDataProvider = SongDataProvider(FakeTextAssetReader, testPlatformPaths)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModelLifecycle.tearDown(dispatcher)
    }

    @Test
    fun preloadAttemptsEveryLowUrlBeforePersistingDone(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache()
        val viewModel = createViewModel(settings, preloader, artworkFileCache = artworkCache)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.songs.showPreloadDialog)

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
        assertTrue(viewModel.uiState.value.songs.illustrationReady)
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertEquals(2, viewModel.uiState.value.songs.preloadCompleted)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals(listOf("song-a.0", "song-b.0"), artworkCache.downloadedThumbnails.map { it.first }.sorted())
    }

    @Test
    fun completedMarkerWithMissingThumbnailsRequestsResync(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val artworkCache = RecordingStandardArtworkCache(thumbnailsPresent = false)
        val viewModel = createViewModel(
            settingsRepository = settings,
            preloader = RecordingPreloader(),
            artworkFileCache = artworkCache
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.songs.showPreloadDialog)
        assertTrue(viewModel.uiState.value.songs.illustrationReady)
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
        assertTrue(viewModel.uiState.value.songs.illustrationReady)
        assertFalse(viewModel.uiState.value.songs.showPreloadDialog)
        assertEquals("部分曲绘图片未能加载", viewModel.uiState.value.sync.error)
        assertEquals(2, viewModel.uiState.value.songs.preloadCompleted)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
    }

    @Test
    fun tabTransitionsDoNotRestartActiveHomeWorkOrResetSiblingState(): Unit = runTest(dispatcher) {
        val repository = ControlledHomeWorkRepository()
        val artworkCache = ControlledThumbnailCache()
        val viewModel = createViewModel(
            settingsRepository = FakeSettingsRepository(preloadDone = false, autoCheckUpdate = false),
            artworkFileCache = artworkCache,
            repository = repository
        )
        val tabs = HomeTabState()
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        viewModel.refresh()
        viewModel.setSuggestTargetInput("invalid")
        viewModel.fetchApiRankByPosition(7)
        runCurrent()

        assertEquals(1, repository.syncCallCount)
        assertEquals(1, repository.rankByPositionCallCount)
        assertEquals(2, repository.networkCallCount)
        assertEquals(2, artworkCache.requests.size)
        assertTrue(viewModel.uiState.value.sync.isSyncing)
        assertTrue(viewModel.uiState.value.songs.isPreloading)

        repeat(3) {
            tabs.select(HomeTab.Songs)
            tabs.select(HomeTab.Tools)
            tabs.select(HomeTab.Profile)
            tabs.select(HomeTab.B30)
        }
        assertEquals(HomeTab.B30, tabs.selected)
        assertEquals(1, repository.syncCallCount)
        assertEquals(1, repository.rankByPositionCallCount)
        assertEquals(2, artworkCache.requests.size)

        artworkCache.complete("song-a.0")
        repository.completeRankByPosition()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.songs.preloadCompleted)
        assertEquals(0.5f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals("查询未成功，请检查网络或稍后重试", viewModel.uiState.value.tools.apiRankByPosition.message)
        assertEquals("invalid", viewModel.uiState.value.tools.suggestTargetInput)
        assertNotNull(viewModel.uiState.value.tools.suggestTargetError)

        repeat(2) {
            tabs.select(HomeTab.Tools)
            tabs.select(HomeTab.Songs)
        }
        assertEquals(1, repository.syncCallCount)
        assertEquals(1, repository.rankByPositionCallCount)
        assertEquals(2, repository.networkCallCount)
        assertEquals(2, artworkCache.requests.size)
        assertTrue(viewModel.uiState.value.sync.isSyncing)
        assertEquals(0.5f, viewModel.uiState.value.songs.preloadProgress)

        repository.completeSync()
        artworkCache.complete("song-b.0")
        advanceUntilIdle()
        assertEquals("部分曲绘图片未能加载", viewModel.uiState.value.sync.error)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals("查询未成功，请检查网络或稍后重试", viewModel.uiState.value.tools.apiRankByPosition.message)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.sync.error)
        assertEquals(1f, viewModel.uiState.value.songs.preloadProgress)
        assertEquals("查询未成功，请检查网络或稍后重试", viewModel.uiState.value.tools.apiRankByPosition.message)
        assertEquals("invalid", viewModel.uiState.value.tools.suggestTargetInput)
        assertNotNull(viewModel.uiState.value.tools.suggestTargetError)
    }

    @Test
    fun getLowIllustrationUrlReturnsIllLowPath(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache()
        val viewModel = createViewModel(settings, preloader, artworkFileCache = artworkCache)
        val url = viewModel.getLowIllustrationUrl("song-a")
        assertEquals("https://example.test/illLow/song-a.png", url)
        assertTrue(
            artworkCache.downloadedThumbnails.isEmpty(),
            "A thumbnail cache miss must only return the remote fallback; it must not start a download"
        )
    }

    @Test
    fun illustrationUrisPreferLocalThumbnailAndStandardArtworkCaches(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val artworkCache = RecordingStandardArtworkCache(
            cachedThumbnails = mapOf("song-a" to "/cache/thumbnail/song-a.png"),
            cachedStandard = mapOf("song-a" to "/cache/standard/song-a.png")
        )
        val viewModel = createViewModel(settings, preloader, artworkFileCache = artworkCache)

        assertEquals("/cache/thumbnail/song-a.png", viewModel.getLowIllustrationUrl("song-a"))
        assertEquals("/cache/standard/song-a.png", viewModel.getCachedOrStandardIllustrationUri("song-a"))
        assertTrue(artworkCache.downloadedThumbnails.isEmpty())
        assertTrue(artworkCache.downloaded.isEmpty())
    }

    @Test
    fun repositoryPersistenceReadsPopulateStatsAndOrderedSnapshots(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository().apply {
            clearCounts = mapOf(Difficulty.EZ to 2, Difficulty.HD to 3, Difficulty.IN to 4, Difficulty.AT to 1)
            fullComboCount = 5
            phiCount = 2
            snapshots = listOf(
                syncSnapshot(id = 2L, timestamp = 2_000L, dataCount = 1),
                syncSnapshot(id = 1L, timestamp = 1_000L, dataCount = 1)
            )
            historyBySnapshot = mapOf(
                2L to listOf(syncHistory(snapshotId = 2L, songId = "song-a.0", difficulty = "IN", timestamp = 2_001L)),
                1L to listOf(syncHistory(snapshotId = 1L, songId = "song-b.0", difficulty = "HD", timestamp = 1_001L))
            )
        }

        val viewModel = createViewModel(settings, repository = repository)
        advanceUntilIdle()

        assertEquals(mapOf("EZ" to 2, "HD" to 3, "IN" to 4, "AT" to 1), viewModel.uiState.value.profile.clearCounts)
        assertEquals(5, viewModel.uiState.value.profile.fcCount)
        assertEquals(2, viewModel.uiState.value.profile.phiCount)
        assertEquals(listOf(2L, 1L), viewModel.uiState.value.tools.syncSnapshots.map { it.id })
        assertEquals(listOf("song-a.0", "song-b.0"), viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId })
        assertEquals(0, repository.syncCallCount)
        assertEquals(0, repository.networkCallCount)
    }

    @Test
    fun repositoryPersistenceReadsKeepEmptyStateAndSkipUnknownDifficulty(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
        val repository = FakePhigrosRepository().apply {
            snapshots = listOf(syncSnapshot(id = 1L, timestamp = 1_000L, dataCount = 1))
            historyBySnapshot = mapOf(
                1L to listOf(syncHistory(snapshotId = 1L, songId = "song-a.0", difficulty = "UNKNOWN"))
            )
        }

        val viewModel = createViewModel(settings, repository = repository)
        advanceUntilIdle()

        assertEquals(emptyMap(), viewModel.uiState.value.profile.clearCounts)
        assertEquals(0, viewModel.uiState.value.profile.fcCount)
        assertEquals(0, viewModel.uiState.value.profile.phiCount)
        assertTrue(viewModel.uiState.value.profile.recentSyncedRecords.isEmpty())
        assertNull(viewModel.uiState.value.profile.lastSyncedRecord)
        assertEquals(0, repository.syncCallCount)
        assertEquals(0, repository.networkCallCount)
    }

    @Test
    fun repositoryFakeTracksNetworkSeparatelyFromSync(): Unit = runTest(dispatcher) {
        val repository = FakePhigrosRepository()

        runCatching { repository.validateToken("token", Server.CN) }
        repository.apiTest()
        repository.fetchLatestRelease(includePreRelease = false)
        runCatching { repository.syncSave("token", Server.CN, SyncMode.Refresh) }

        assertEquals(1, repository.syncCallCount)
        assertEquals(4, repository.networkCallCount)
    }

    @Test
    fun recentEffectiveSyncHistoryLoadsAllEntriesFromLatestThreeEffectiveSnapshots(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = false)
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
        val repository = FakePhigrosRepository().apply {
            this.snapshots = snapshots
            historyBySnapshot = history
        }
        val viewModel = createViewModel(settingsRepository = settings, repository = repository)
        advanceUntilIdle()

        assertEquals(4_000L, viewModel.uiState.value.profile.lastSyncTime)
        assertEquals(
            listOf("song-a.0", "song-b.0", "song-a.0", "song-b.0"),
            viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId },
            "History should include every entry from the latest three effective snapshots"
        )
        assertEquals("song-a.0", viewModel.uiState.value.profile.lastSyncedRecord?.songId)
        assertEquals(Difficulty.IN, viewModel.uiState.value.profile.lastSyncedRecord?.difficulty)
        assertEquals(0, repository.syncCallCount)
        assertEquals(0, repository.networkCallCount)
    }

    @Test
    fun noChangeSyncDoesNotInsertSnapshotOrHistory(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
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
            thumbnailPreloader = preloader
        ).let(viewModelLifecycle::track)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sync.isSyncing, "Sync should be complete")
        assertEquals(null, viewModel.uiState.value.sync.error, "Sync should have no error")
        assertEquals(2_000L, viewModel.uiState.value.profile.lastSyncTime)
        assertEquals(listOf(SyncMode.Refresh), repository.syncModes)
        assertEquals(0, recordDao.getAllRecordsOnceCallCount, "Home must not re-read records after sync")
        assertEquals(emptyList(), viewModel.uiState.value.profile.recentSyncedRecords)
        assertNull(viewModel.uiState.value.profile.lastSyncedRecord)
        assertEquals(emptyList(), repository.getSyncSnapshotsOnce())
    }

    @Test
    fun changedSyncConsumesAtomicResultWithoutWritingPersistence(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
        val committedSnapshot = syncSnapshot(id = 1L, timestamp = 2_000L, dataCount = 1)
        val committedHistory = syncHistory(
            snapshotId = 1L,
            songId = "song-a.0",
            difficulty = "IN",
            score = 950_000,
            accuracy = 95f,
            isFullCombo = true,
            timestamp = 2_000L
        )
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
            cachedSave = cachedSave,
            changedEntryCount = 1,
            snapshotCreated = true
        ).apply {
            snapshots = listOf(committedSnapshot)
            historyBySnapshot = mapOf(1L to listOf(committedHistory))
        }
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
            thumbnailPreloader = preloader
        ).let(viewModelLifecycle::track)
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sync.isSyncing, "Sync should be complete")
        assertEquals(null, viewModel.uiState.value.sync.error, "Sync should have no error")
        assertEquals(listOf(SyncMode.Refresh), repository.syncModes)
        assertEquals(2_000L, viewModel.uiState.value.profile.lastSyncTime)
        assertEquals(listOf("song-a.0"), viewModel.uiState.value.profile.recentSyncedRecords.map { it.songId })
        assertEquals(listOf(1L), repository.getSyncSnapshotsOnce().map { it.id })
    }

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository,
        preloader: IllustrationThumbnailPreloader = RecordingPreloader(),
        artworkFileCache: StandardArtworkCache = RecordingStandardArtworkCache(),
        songDataProvider: SongDataProvider = testSongDataProvider,
        appVersionName: String = "",
        repository: PhigrosRepository = FakePhigrosRepository()
    ): HomeViewModel {
        val illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
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
            appVersionNameProvider = { appVersionName }
        ).let(viewModelLifecycle::track)
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

    private class ControlledHomeWorkRepository : FakePhigrosRepository() {
        private val syncGate = CompletableDeferred<Unit>()
        private val rankGate = CompletableDeferred<Unit>()
        var rankByPositionCallCount: Int = 0
            private set

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            syncCallCount++
            networkCallCount++
            syncGate.await()
            return Result.failure(IllegalStateException("sync failed"))
        }

        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> {
            rankByPositionCallCount++
            networkCallCount++
            rankGate.await()
            return Result.failure(IllegalStateException("rank failed"))
        }

        fun completeSync() {
            syncGate.complete(Unit)
        }

        fun completeRankByPosition() {
            rankGate.complete(Unit)
        }
    }

    private class ControlledThumbnailCache : StandardArtworkCache {
        private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val requests = mutableListOf<String>()

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            requests += songId
            gates.getOrPut(songId) { CompletableDeferred() }.await()
            if (songId == "song-b.0") error("thumbnail failed")
            return url
        }

        fun complete(songId: String) {
            gates.getValue(songId).complete(Unit)
        }

        override fun getThumbnailIfPresent(songId: String): String? = null
        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = false
        override fun clearThumbnails(songIds: Iterable<String>) = Unit
        override fun clearAllThumbnails() = Unit
        override suspend fun getOrDownloadStandard(songId: String, url: String): String = url
        override fun getStandardIfPresent(songId: String): String? = null
        override fun clearStandard(songIds: Iterable<String>) = Unit
        override fun clearAllStandard() = Unit
    }

    private class RecordingStandardArtworkCache(
        private var thumbnailsPresent: Boolean = true,
        private val cachedThumbnails: Map<String, String> = emptyMap(),
        private val cachedStandard: Map<String, String> = emptyMap()
    ) : StandardArtworkCache {
        val downloaded: MutableList<Pair<String, String>> = mutableListOf()
        val downloadedThumbnails: MutableList<Pair<String, String>> = mutableListOf()
        val clearedThumbnails: MutableList<String> = mutableListOf()
        val clearedStandard: MutableList<String> = mutableListOf()
        var clearAllCalled: Boolean = false

        override suspend fun getOrDownloadThumbnail(songId: String, url: String): String {
            downloadedThumbnails += songId to url
            // Keep existing preloader assertions focused on request coverage.
            return url
        }

        override fun getThumbnailIfPresent(songId: String): String? = cachedThumbnails[songId]

        override fun hasAllThumbnails(songIds: Iterable<String>): Boolean = thumbnailsPresent

        override fun clearThumbnails(songIds: Iterable<String>) {
            clearedThumbnails += songIds
        }

        override fun clearAllThumbnails() {
            clearAllCalled = true
        }

        override suspend fun getOrDownloadStandard(songId: String, url: String): String {
            downloaded += songId to url
            return "/cache/standard/$songId.png"
        }

        override fun getStandardIfPresent(songId: String): String? = cachedStandard[songId]

        override fun clearStandard(songIds: Iterable<String>) {
            clearedStandard += songIds
        }

        override fun clearAllStandard() {
            clearAllCalled = true
        }
    }

    private class FakeSettingsRepository(
        preloadDone: Boolean,
        autoCheckUpdate: Boolean = true,
        includePreRelease: Boolean = false,
        apiEnabled: Boolean = false,
        useApiData: Boolean = false,
        apiUserId: String = "",
        apiPlatform: String = "",
        apiPlatformId: String = ""
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
        override val apiEnabled: Flow<Boolean> = flowOf(apiEnabled)
        override suspend fun setApiEnabled(enabled: Boolean) = Unit
        override val useApiData: Flow<Boolean> = flowOf(useApiData)
        override suspend fun setUseApiData(useApiData: Boolean) = Unit
        override val apiId: Flow<String> = flowOf(apiUserId)
        override suspend fun setApiId(apiId: String) = Unit
        override val apiPlatform: Flow<String> = flowOf(apiPlatform)
        override suspend fun setApiPlatform(platform: String) = Unit
        override val apiPlatformId: Flow<String> = flowOf(apiPlatformId)
        override suspend fun setApiPlatformId(platformId: String) = Unit
        override val crashNotificationGuideShown: Flow<Boolean> = flowOf(false)
        override suspend fun setCrashNotificationGuideShown(shown: Boolean) = Unit
    }

    private open class FakePhigrosRepository : PhigrosRepository {
        var syncCallCount: Int = 0
        var networkCallCount: Int = 0
        var clearCounts: Map<Difficulty, Int> = emptyMap()
        var fullComboCount: Int = 0
        var phiCount: Int = 0
        var snapshots: List<SyncSnapshot> = emptyList()
        var songHistory: Map<String, List<SongSyncHistoryEntry>> = emptyMap()
        var historyBySnapshot: Map<Long, List<SongSyncHistoryEntry>> = emptyMap()
        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> {
            networkCallCount++
            error("Not needed for this test")
        }

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            syncCallCount++
            networkCallCount++
            error("Not needed for this test")
        }

        private fun <T> networkResult(result: Result<T>): Result<T> {
            networkCallCount++
            return result
        }

        override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = clearCounts
        override suspend fun getTotalFullComboCount(): Int = fullComboCount
        override suspend fun getTotalPhiCount(): Int = phiCount
        override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(snapshots)
        override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = snapshots
        override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> = flowOf(songHistory[songId].orEmpty())
        override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> = historyBySnapshot[snapshotId].orEmpty()

        override fun getCachedSave(): Flow<Save?> = MutableStateFlow(emptySave())
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) = Unit
        override suspend fun getSessionToken(): Pair<String, Server>? = Pair("fake-token", Server.CN)
        override suspend fun clearData() = Unit
        override suspend fun clearTokenSync() = Unit

        override suspend fun apiTest(): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun getSongApiDetail(key: org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey): Result<org.kasumi321.ushio.phitracker.domain.model.SongApiDetail> =
            Result.failure(IllegalStateException("Song detail is route-owned"))
        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetSaveHistory(platform: String, platformId: String, apiUserId: String, request: List<String>): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetRankByUser(platform: String, platformId: String, apiUserId: String): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))
        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
            networkResult(Result.failure(IllegalStateException("Not implemented in Phase B")))

        open var fetchLatestReleaseCallCount = 0
        val fetchLatestReleaseIncludePreReleaseValues = mutableListOf<Boolean>()
        open var fetchLatestReleaseResult: Result<ReleaseInfo> =
            Result.failure(IllegalStateException("Not configured"))

        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo> {
            fetchLatestReleaseCallCount++
            fetchLatestReleaseIncludePreReleaseValues.add(includePreRelease)
            return networkResult(fetchLatestReleaseResult)
        }
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

    private class StatefulRecordDao(
        initialRecords: List<RecordEntity>,
        private val postSyncRecords: List<RecordEntity>
    ) : RecordDao {
        var getAllRecordsOnceCallCount = 0
            private set
        private var currentRecords = initialRecords.toMutableList()

        override suspend fun insertAll(records: List<RecordEntity>) {
            currentRecords.clear()
            currentRecords.addAll(records)
        }

        override fun getAllRecords(): Flow<List<RecordEntity>> = flowOf(currentRecords.toList())
        override suspend fun getAllRecordsOnce(): List<RecordEntity> {
            val records = if (getAllRecordsOnceCallCount == 0) currentRecords.toList() else postSyncRecords.toList()
            getAllRecordsOnceCallCount++
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
        private val cachedSave: Save? = emptySave(),
        private val committedAt: Long = 2_000L,
        private val changedEntryCount: Int = 0,
        private val snapshotCreated: Boolean = false
    ) : FakePhigrosRepository() {
        val syncModes = mutableListOf<SyncMode>()
        override fun getCachedSave(): Flow<Save?> = flowOf(cachedSave)

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            syncModes += mode
            return syncResult.map { save ->
                SyncSaveResult(save, committedAt, changedEntryCount, snapshotCreated)
            }
        }
    }

    private object FakeTextAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\nsong-a\tSong A\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-b\tSong B\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0"
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
            "info.csv" -> buildString {
                appendLine("id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT")
                songIds.forEach { songId -> appendLine("$songId\tSong $songId\tComposer\tIllustrator\t\t\t\t\t1.0\t2.0\t3.0\t4.0") }
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
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "All songs should be visible with no chapter filter")

        // Toggle "Single" — songs in "Single" chapter: song-a.0 and song-c.0
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.songs.filteredSongs.size,
            "2 songs in Single chapter")
        assertTrue(viewModel.uiState.value.songs.filteredSongs.all { it.chapter == "Single" })

        // Toggle also "Collection" — now both chapters active
        viewModel.toggleChapter("Collection")
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "Stacking both chapters shows all 3 songs")
        assertEquals(setOf("Single", "Collection"), viewModel.uiState.value.songs.selectedChapters)
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
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "Both chapters active")
        assertEquals(setOf("Single", "Collection"), viewModel.uiState.value.songs.selectedChapters)

        // Toggle off "Single" — only "Collection" remains
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.songs.filteredSongs.size,
            "Only Collection chapter songs after toggling Single off")
        assertTrue(viewModel.uiState.value.songs.filteredSongs.all { it.chapter == "Collection" })
        assertEquals(setOf("Collection"), viewModel.uiState.value.songs.selectedChapters)
    }

    @Test
    fun clearChaptersRestoresAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply chapter filter
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.songs.filteredSongs.size,
            "Filtered to Single chapter")
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isNotEmpty())

        // Clear chapters
        viewModel.clearChapters()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "All songs restored after clearing chapters")
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty(),
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
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isNotEmpty())
        assertNotNull(viewModel.uiState.value.songs.selectedDifficulty)

        // Reset
        viewModel.resetFilters()
        advanceUntilIdle()

        // All filters cleared
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty())
        assertEquals(null, viewModel.uiState.value.songs.selectedDifficulty)
        assertEquals(1, viewModel.uiState.value.songs.minLevel)
        assertEquals(17, viewModel.uiState.value.songs.maxLevel)
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size,
            "Reset should restore all songs")
    }

    @Test
    fun emptyChaptersMatchesAllSongs(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // selectedChapters starts empty — all songs match
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty())
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size)

        // Filter by difficulty only — chapters still empty, difficulty narrows
        viewModel.filterByDifficulty(Difficulty.IN)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.songs.selectedChapters.isEmpty())
        // All 3 songs have IN=3.0, min=1 max=16 → all should match
        assertEquals(3, viewModel.uiState.value.songs.filteredSongs.size)
    }

    @Test
    fun chapterFilterStacksWithSearch(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val viewModel = createChapterFilterViewModel(settings)
        advanceUntilIdle()

        // Apply chapter filter first
        viewModel.toggleChapter("Single")
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.songs.filteredSongs.size)

        // Add search query that narrows further
        viewModel.searchSongs("Song A")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.songs.filteredSongs.size,
            "Search + chapter filter should stack")
        assertEquals("song-a.0", viewModel.uiState.value.songs.filteredSongs.first().id)
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
        assertTrue(viewModel.uiState.value.tools.suggestItems.isEmpty(),
            "Empty B30 should yield empty suggestions")
    }

    @Test
    fun noCachedSaveYieldsEmptySuggestions(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true)
        val preloader = RecordingPreloader()
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
            thumbnailPreloader = preloader
        ).let(viewModelLifecycle::track)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.tools.suggestItems.isEmpty(),
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
            thumbnailPreloader = preloader
        ).let(viewModelLifecycle::track)
        advanceUntilIdle()

        // B30 has only 1 record (< 20 minimum) → empty suggestions
        assertTrue(viewModel.uiState.value.tools.suggestItems.isEmpty(),
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

        assertEquals("16.123", viewModel.uiState.value.tools.suggestTargetInput)
        assertEquals(
            "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数",
            viewModel.uiState.value.tools.suggestTargetError
        )

        viewModel.setSuggestTargetInput("abc")
        advanceUntilIdle()

        assertEquals("abc", viewModel.uiState.value.tools.suggestTargetInput)
        assertEquals(
            "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数",
            viewModel.uiState.value.tools.suggestTargetError
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
            thumbnailPreloader = RecordingPreloader()
        ).let(viewModelLifecycle::track)
    }

    private object ChapterTestAssetReader : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "tips.txt" -> "Tip: test"
            "info.csv" -> "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\nsong-a\tSong A\tComposer\tIllus\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-b\tSong B\tComposer\tIllus\t\t\t\t\t1.0\t2.0\t3.0\t4.0\nsong-c\tSong C\tComposer\tIllus\t\t\t\t\t1.0\t2.0\t3.0\t4.0"
            "infolist.json" -> """{"song-a":{"chapter":"Single"},"song-b":{"chapter":"Collection"},"song-c":{"chapter":"Single"}}"""
            "notesInfo.json" -> "{}"
            else -> error("Test asset not found: $name")
        }
    }

    // ---- Phase G: auto-update core tests ----

    @Test
    fun startupAutoCheckEnabledFetchesAndSetsAvailable(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = true, autoCheckUpdate = true)
        val repository = FakePhigrosRepository().apply {
            fetchLatestReleaseResult = Result.success(
                ReleaseInfo(
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
        val state = viewModel.uiState.value.sync.updateCheckState
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
        assertTrue(viewModel.uiState.value.sync.updateCheckState is UpdateCheckState.Idle,
            "UpdateCheckState should remain Idle")
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
        ): SyncSnapshot = SyncSnapshot(
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
            score: Int = 900_000,
            accuracy: Float = 90f,
            isFullCombo: Boolean = false,
            timestamp: Long = 1_000L
        ): SongSyncHistoryEntry = SongSyncHistoryEntry(
            id = 0L,
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
