package org.kasumi321.ushio.phitracker.ui.song

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongApiDetail
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.ui.settings.FakePhigrosRepository
import org.kasumi321.ushio.phitracker.ui.settings.FakeSettingsRepository
import org.kasumi321.ushio.phitracker.ui.settings.TestAssets
import org.kasumi321.ushio.phitracker.ui.settings.b30FixtureSave
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailViewModelTest {
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
    fun exposesLoadingThenFoundStateForCurrentRoute() = runTest(dispatcher) {
        // Given
        val viewModel = createViewModel(songId = "song-a.0")

        // When
        val beforeLoad = viewModel.uiState.value
        advanceUntilIdle()

        // Then
        assertTrue(beforeLoad.isLoading)
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.notFound)
        assertEquals("song-a.0", viewModel.uiState.value.songInfo?.id)
    }

    @Test
    fun exposesNotFoundAfterLoadingForMissingRouteSong() = runTest(dispatcher) {
        // Given
        val viewModel = createViewModel(songId = "missing.0")

        // When
        advanceUntilIdle()

        // Then
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.notFound)
        assertNull(viewModel.uiState.value.songInfo)
    }

    @Test
    fun usesExactB30FallbackWhenProfileRksIsZeroOrMissing() = runTest(dispatcher) {
        // Given
        val repository = FakePhigrosRepository().apply {
            cachedSave = b30FixtureSave()
            profile = UserProfile("player", "Player", "", "", "", 0f, 0, 0, "")
        }
        val viewModel = createViewModel(songId = "song-a.0", repository = repository)

        // When
        advanceUntilIdle()

        // Then
        val songARks = ((99f - 55f) / 45f).let { it * it * 3f }
        val songBRks = ((98f - 55f) / 45f).let { it * it * 3f }
        assertEquals((songARks + songBRks) / 30f, viewModel.uiState.value.displayRks)
        assertEquals(1, viewModel.uiState.value.userRecords.size)
        assertEquals(Difficulty.IN, viewModel.uiState.value.userRecords.single().difficulty)
    }

    @Test
    fun loadsApiDetailIntoRouteStateWithExactCompositeKey() = runTest(dispatcher) {
        // Given
        val settings = FakeSettingsRepository().apply {
            setApiEnabled(true)
            setUseApiData(true)
            setApiPlatform(" taptap ")
            setApiPlatformId(" player-id ")
        }
        val history = historyEntry(snapshotId = 8L)
        val repository = FakePhigrosRepository().apply {
            songApiDetail = Result.success(SongApiDetail(7, 12, 98.5f, 6, listOf(history)))
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.loadSongApiDetail(Difficulty.IN)
        advanceUntilIdle()

        // Then
        val detail = viewModel.getSongApiDetail(Difficulty.IN)
        assertEquals(7, detail.userRank)
        assertEquals(12, detail.totalUsers)
        assertEquals(98.5f, detail.avgAcc)
        assertEquals(6, detail.avgAccCount)
        assertEquals(listOf(history), detail.history)
        val request = repository.songApiDetailRequests.single()
        assertEquals("taptap", request.platform)
        assertEquals("player-id", request.platformId)
        assertEquals("song-a.0", request.songId)
        assertEquals((viewModel.uiState.value.displayRks - 0.015f).coerceAtLeast(0f), request.minRks)
        assertEquals(viewModel.uiState.value.displayRks + 0.015f, request.maxRks)
    }

    @Test
    fun disabledOrIncompleteApiSettingsMakeNoRequest() = runTest(dispatcher) {
        // Given
        val settings = FakeSettingsRepository()
        val repository = FakePhigrosRepository()
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.loadSongApiDetail(Difficulty.IN)
        settings.setApiEnabled(true)
        settings.setUseApiData(true)
        advanceUntilIdle()
        viewModel.loadSongApiDetail(Difficulty.IN)
        advanceUntilIdle()

        // Then
        assertTrue(repository.songApiDetailRequests.isEmpty())
    }

    @Test
    fun identityChangeClearsAccountAStateBeforeAccountBLoad() = runTest(dispatcher) {
        // Given
        val settings = FakeSettingsRepository().apply {
            setApiEnabled(true)
            setUseApiData(true)
            setApiPlatform("taptap")
            setApiPlatformId("account-a")
        }
        val repository = FakePhigrosRepository().apply {
            songApiDetail = Result.success(SongApiDetail(7, 12, 98.5f, 6, emptyList()))
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()
        viewModel.loadSongApiDetail(Difficulty.IN)
        advanceUntilIdle()
        assertEquals(7, viewModel.getSongApiDetail(Difficulty.IN).userRank)

        // When
        settings.setApiPlatformId("account-b")
        advanceUntilIdle()

        // Then
        assertNull(viewModel.getSongApiDetail(Difficulty.IN).userRank)
        viewModel.loadSongApiDetail(Difficulty.IN)
        advanceUntilIdle()
        assertEquals(listOf("account-a", "account-b"), repository.songApiDetailRequests.map { it.platformId })
    }

    @Test
    fun routeOwnsPersistedHistoryAndRemoteIllustrationUrls() = runTest(dispatcher) {
        // Given
        val history = historyEntry(snapshotId = 3L)
        val repository = FakePhigrosRepository().apply { songHistory = listOf(history) }
        val viewModel = createViewModel("song-a.0", repository)

        // When
        advanceUntilIdle()

        // Then
        assertEquals(listOf(history), viewModel.uiState.value.syncHistory)
        assertTrue(viewModel.uiState.value.lowIllustrationUrl.orEmpty().contains("/illLow/song-a.png"))
        assertTrue(viewModel.uiState.value.standardIllustrationUrl.orEmpty().contains("/ill/song-a.png"))
        assertFalse(viewModel.uiState.value.standardIllustrationUrl.orEmpty().contains("/cache/"))
    }

    private fun createViewModel(
        songId: String,
        repository: FakePhigrosRepository = FakePhigrosRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository()
    ): SongDetailViewModel = SongDetailViewModel(
        songId = songId,
        initialDifficulty = Difficulty.IN,
        repository = repository,
        settingsRepository = settingsRepository,
        songDataProvider = SongDataProvider(assetReader = TestAssets),
        illustrationProvider = IllustrationProvider()
    )

    private fun historyEntry(snapshotId: Long) = SongSyncHistoryEntry(
        id = snapshotId,
        snapshotId = snapshotId,
        songId = "song-a.0",
        difficulty = "IN",
        score = 990_000,
        accuracy = 99f,
        isFullCombo = false,
        timestamp = snapshotId
    )
}
