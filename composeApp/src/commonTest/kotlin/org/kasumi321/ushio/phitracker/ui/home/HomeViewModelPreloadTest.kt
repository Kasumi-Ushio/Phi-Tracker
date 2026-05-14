package org.kasumi321.ushio.phitracker.ui.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPreloadTest {
    private val dispatcher = StandardTestDispatcher()

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
    fun failedPreloadDoesNotPersistDoneAndKeepsDialogRetryable(): Unit = runTest(dispatcher) {
        val settings = FakeSettingsRepository(preloadDone = false)
        val preloader = RecordingPreloader(failOnUrl = "https://example.test/illLow/song-b.png")
        val viewModel = createViewModel(settings, preloader)
        advanceUntilIdle()

        viewModel.startPreloadIllustrations()
        advanceUntilIdle()

        assertFalse(settings.preloadDone)
        assertFalse(viewModel.uiState.value.illustrationReady)
        assertTrue(viewModel.uiState.value.showPreloadDialog)
        assertEquals("部分预览图加载失败", viewModel.uiState.value.error)
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

    private fun createViewModel(
        settingsRepository: FakeSettingsRepository,
        preloader: IllustrationThumbnailPreloader,
        cacheClearFn: suspend (List<String>) -> Unit = {}
    ): HomeViewModel {
        val repository = FakePhigrosRepository()
        val illustrationProvider = IllustrationProvider().apply { setBaseUrl("https://example.test") }
        return HomeViewModel(
            repository = repository,
            getB30UseCase = GetB30UseCase(repository),
            syncSaveUseCase = SyncSaveUseCase(repository),
            searchSongUseCase = SearchSongUseCase(),
            songDataProvider = SongDataProvider(FakeTextAssetReader),
            illustrationProvider = illustrationProvider,
            tipsProvider = TipsProvider(FakeTextAssetReader),
            settingsRepository = settingsRepository,
            thumbnailPreloader = preloader,
            clearCacheUrlsFn = cacheClearFn
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

    private class FakeSettingsRepository(preloadDone: Boolean) : SettingsRepository {
        override val themeMode: Flow<Int> = flowOf(0)
        override val showB30Overflow: Flow<Boolean> = flowOf(false)
        override val overflowCount: Flow<Int> = flowOf(9)
        var preloadDone = preloadDone
            private set

        override suspend fun setThemeMode(mode: Int) = Unit
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
        override val includePreRelease: Flow<Boolean> = flowOf(false)
        override suspend fun setIncludePreRelease(enabled: Boolean) = Unit
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
    }

    private class FakePhigrosRepository : PhigrosRepository {
        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> {
            error("Not needed for this test")
        }

        override suspend fun syncSave(sessionToken: String, server: Server): Result<Save> {
            error("Not needed for this test")
        }

        override fun getCachedSave(): Flow<Save?> = MutableStateFlow(emptySave())
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) = Unit
        override suspend fun getSessionToken(): Pair<String, Server>? = null
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
        override suspend fun apiGetFittedDifficulty(songId: String, difficulty: String): Result<JsonObject> =
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

    private companion object {
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
    }
}
