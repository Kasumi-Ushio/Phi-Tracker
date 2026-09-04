package org.kasumi321.ushio.phitracker.ui.song

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.ChartTagVoteCount
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongApiDetail
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.usecase.GetChartTagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.VoteChartTagsUseCase
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
            setApiId(" api-user ")
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
        assertEquals("api-user", request.apiUserId)
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
            setApiId("api-user")
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

    @Test
    fun localPersistentArtworkUrisWinOverRemoteFallbacks() = runTest(dispatcher) {
        // Given
        val cache = RouteArtworkCache(
            thumbnailUri = "/persistent/thumbnail/song-a.0.png",
            standardUri = "/persistent/standard/song-a.0.png"
        )
        val viewModel = createViewModel(
            songId = "song-a.0",
            illustrationUriResolver = IllustrationUriResolver(cache, IllustrationProvider())
        )

        // When
        advanceUntilIdle()

        // Then
        assertEquals("/persistent/thumbnail/song-a.0.png", viewModel.uiState.value.lowIllustrationUrl)
        assertEquals("/persistent/standard/song-a.0.png", viewModel.uiState.value.standardIllustrationUrl)
        assertEquals(0, cache.downloadCalls)
    }

    private fun createViewModel(
        songId: String,
        repository: FakePhigrosRepository = FakePhigrosRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        illustrationUriResolver: IllustrationUriResolver = IllustrationUriResolver(
            NoOpStandardArtworkCache,
            IllustrationProvider()
        )
    ): SongDetailViewModel = SongDetailViewModel(
        songId = songId,
        initialDifficulty = Difficulty.IN,
        repository = repository,
        settingsRepository = settingsRepository,
        songDataProvider = SongDataProvider(assetReader = TestAssets),
        illustrationUriResolver = illustrationUriResolver,
        getChartTagsUseCase = GetChartTagsUseCase(repository),
        voteChartTagsUseCase = VoteChartTagsUseCase(repository)
    )

    @Test
    fun loadsChartTagsWithVotedTagsForDisplayAndFullSkeletonForPicker() = runTest(dispatcher) {
        // Given
        val settings = apiSettings()
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(chartTagTreeFixture())
            chartTagData = Result.success(chartTagDataFixture())
            myChartTagVotes = Result.success(setOf("连打"))
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.loadChartTags(Difficulty.IN)
        advanceUntilIdle()

        // Then
        val state = viewModel.getChartTagState(Difficulty.IN)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(
            listOf(
                ChartTagVoteCount(
                    name = "高速", votes = 12, primaryVotes = 8, secondaryVotes = 4
                ),
                ChartTagVoteCount(
                    name = "连打", votes = 3, primaryVotes = 0, secondaryVotes = 3, isMine = true
                )
            ),
            state.categories.single { it.name == "配置" }.tags
        )
        assertEquals(
            listOf("高速", "连打", "多指"),
            state.allCategories.flatMap { category -> category.tags.map { it.name } }
        )
        assertEquals(
            listOf("song-a.0" to Difficulty.IN),
            repository.chartTagDataRequests
        )
        assertEquals(
            listOf(listOf("song-a.0", "IN", "taptap", "player-id", "api-user", "token-1")),
            repository.myChartTagVoteRequests
        )
    }

    @Test
    fun loadChartTagsSkipsUserVotesWhenIdentityIsIncomplete() = runTest(dispatcher) {
        // Given
        val settings = FakeSettingsRepository().apply { setApiEnabled(true) }
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(chartTagTreeFixture())
            chartTagData = Result.success(chartTagDataFixture())
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.loadChartTags(Difficulty.IN)
        advanceUntilIdle()

        // Then
        val state = viewModel.getChartTagState(Difficulty.IN)
        assertFalse(state.isLoading)
        assertEquals(listOf("高速", "连打"), state.categories.single { it.name == "配置" }.tags.map { it.name })
        assertTrue(state.categories.single { it.name == "配置" }.tags.none { it.isMine })
        assertTrue(repository.myChartTagVoteRequests.isEmpty())
    }

    @Test
    fun loadChartTagsFailureExposesRetryableError() = runTest(dispatcher) {
        // Given
        val settings = apiSettings()
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.failure(IllegalStateException("boom"))
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.loadChartTags(Difficulty.IN)
        advanceUntilIdle()

        // Then
        val state = viewModel.getChartTagState(Difficulty.IN)
        assertFalse(state.isLoading)
        assertEquals("标签数据获取失败，请稍后重试", state.error)
        assertTrue(state.categories.isEmpty())
    }

    @Test
    fun voteSubmissionWithoutApiTokenFailsFastWithoutSendingRequest() = runTest(dispatcher) {
        // Given
        val settings = apiSettings(apiToken = "")
        val repository = FakePhigrosRepository()
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.submitChartTagVote(Difficulty.IN, listOf("高速"), emptyList())
        advanceUntilIdle()

        // Then
        val state = viewModel.getChartTagState(Difficulty.IN)
        assertFalse(state.voteSubmitting)
        assertTrue(state.voteError.orEmpty().contains("API Token"))
        assertTrue(repository.voteChartTagRequests.isEmpty())
    }

    @Test
    fun successfulVoteReloadsTagsAndMarksVoteSucceeded() = runTest(dispatcher) {
        // Given
        val settings = apiSettings(apiToken = "token-1")
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(chartTagTreeFixture())
            chartTagData = Result.success(chartTagDataFixture())
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.submitChartTagVote(Difficulty.IN, listOf("高速"), listOf("多指"))
        advanceUntilIdle()

        // Then
        val request = repository.voteChartTagRequests.single()
        assertEquals("song-a.0", request.songId)
        assertEquals(Difficulty.IN, request.difficulty)
        assertEquals(listOf("高速"), request.primaryTags)
        assertEquals(listOf("多指"), request.secondaryTags)
        assertEquals("taptap", request.platform)
        assertEquals("player-id", request.platformId)
        assertEquals("api-user", request.apiUserId)
        assertEquals("token-1", request.apiToken)
        val state = viewModel.getChartTagState(Difficulty.IN)
        assertFalse(state.voteSubmitting)
        assertNull(state.voteError)
        assertTrue(state.voteSucceeded)
        assertEquals(listOf("高速", "连打"), state.categories.single { it.name == "配置" }.tags.map { it.name })
    }

    private suspend fun apiSettings(apiToken: String = "token-1") = FakeSettingsRepository().apply {
        setApiEnabled(true)
        setApiId("api-user")
        setApiPlatform("taptap")
        setApiPlatformId("player-id")
        setApiToken(apiToken)
    }

    private fun chartTagTreeFixture() = listOf(
        ChartTagTreeNode(
            id = 1L,
            name = "配置",
            description = "谱面配置特征",
            sortOrder = 0,
            children = listOf(
                ChartTagTreeNode(
                    id = 11L, name = "高速", description = null, sortOrder = 0
                ),
                ChartTagTreeNode(
                    id = 12L, name = "连打", description = null, sortOrder = 1
                )
            )
        ),
        ChartTagTreeNode(
            id = 2L,
            name = "手法",
            description = null,
            sortOrder = 1,
            children = listOf(
                ChartTagTreeNode(
                    id = 21L, name = "多指", description = null, sortOrder = 0
                )
            )
        )
    )

    private fun chartTagDataFixture() = ChartTagSongData(
        songId = "song-a.0",
        difficulty = Difficulty.IN,
        tags = mapOf("高速" to 12, "连打" to 3),
        primary = mapOf("高速" to 8),
        secondary = mapOf("高速" to 4, "连打" to 3),
        categories = emptyList()
    )

    private class RouteArtworkCache(
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
