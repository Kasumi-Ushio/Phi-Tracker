package org.kasumi321.ushio.phitracker.ui.song

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.TextAssetReader
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
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.song_detail_tags_load_failed
import phitracker.composeapp.generated.resources.song_detail_vote_no_token

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
    fun declinedPreloadKeepsLowIllustrationBlankWithoutRemoteFallback() = runTest(dispatcher) {
        // Given
        val settings = FakeSettingsRepository().apply { setIllustrationPreloadDeclined(true) }
        val artworkCache = RouteArtworkCache()
        val viewModel = createViewModel(
            songId = "song-a.0",
            settingsRepository = settings,
            illustrationUriResolver = IllustrationUriResolver(
                artworkCache,
                IllustrationProvider().apply { setBaseUrl("https://example.test") }
            )
        )

        // When
        advanceUntilIdle()

        // Then
        assertNull(viewModel.getLowIllustrationUrl(Difficulty.IN))
        assertEquals(0, artworkCache.downloadCalls)
    }

    @Test
    fun missingLocalThumbnailStaysBlankWithoutRemoteFallback() = runTest(dispatcher) {
        // Given
        val artworkCache = RouteArtworkCache()
        val viewModel = createViewModel(
            songId = "song-a.0",
            illustrationUriResolver = IllustrationUriResolver(
                artworkCache,
                IllustrationProvider().apply { setBaseUrl("https://example.test") }
            )
        )

        // When
        advanceUntilIdle()

        // Then: like the song cards, the detail header never triggers an
        // on-demand remote download when the preloaded thumbnail is absent.
        assertNull(viewModel.getLowIllustrationUrl(Difficulty.IN))
        assertEquals(0, artworkCache.downloadCalls)
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
    fun routeOwnsPersistedHistoryAndStandardIllustrationUrls() = runTest(dispatcher) {
        // Given
        val history = historyEntry(snapshotId = 3L)
        val repository = FakePhigrosRepository().apply { songHistory = listOf(history) }
        val viewModel = createViewModel("song-a.0", repository)

        // When
        advanceUntilIdle()

        // Then
        assertEquals(listOf(history), viewModel.uiState.value.syncHistory)
        // Thumbnails are local-only; the standard preview keeps its remote URL.
        assertNull(viewModel.getLowIllustrationUrl(Difficulty.IN))
        assertTrue(
            viewModel.getStandardIllustrationUrl(Difficulty.IN).orEmpty().contains("/ill/song-a.png")
        )
        assertFalse(viewModel.getStandardIllustrationUrl(Difficulty.IN).orEmpty().contains("/cache/"))
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
        assertEquals("/persistent/thumbnail/song-a.0.png", viewModel.getLowIllustrationUrl(Difficulty.IN))
        assertEquals("/persistent/standard/song-a.0.png", viewModel.getStandardIllustrationUrl(Difficulty.IN))
        assertEquals(0, cache.downloadCalls)
    }

    private val perDifficultySongId = IllustrationProvider.PER_DIFFICULTY_SONGS.keys.single()

    @Test
    fun perDifficultySongResolvesIllustrationUrlsPerDifficulty() = runTest(dispatcher) {
        // Given
        val viewModel = createViewModel(
            songId = "$perDifficultySongId.0",
            illustrationUriResolver = IllustrationUriResolver(
                NoOpStandardArtworkCache,
                IllustrationProvider().apply { setBaseUrl("https://example.test") }
            ),
            assetReader = PerDifficultyAssets(perDifficultySongId)
        )

        // When
        advanceUntilIdle()

        // Then: thumbnails are local-only (NoOp cache has none); the standard
        // preview still resolves the per-difficulty remote URL.
        assertNull(viewModel.getLowIllustrationUrl(Difficulty.IN))
        assertEquals(
            "https://example.test/ill/EZ/$perDifficultySongId.png",
            viewModel.getStandardIllustrationUrl(Difficulty.EZ)
        )
    }

    @Test
    fun flatSongKeepsFlatIllustrationUrlsForEveryDifficulty() = runTest(dispatcher) {
        // Given
        val viewModel = createViewModel(
            songId = "song-a.0",
            illustrationUriResolver = IllustrationUriResolver(
                NoOpStandardArtworkCache,
                IllustrationProvider().apply { setBaseUrl("https://example.test") }
            )
        )

        // When
        advanceUntilIdle()

        // Then: local-only thumbnail stays blank; the standard preview keeps
        // the flat remote URL for every difficulty.
        assertNull(viewModel.getLowIllustrationUrl(Difficulty.AT))
        assertEquals("https://example.test/ill/song-a.png", viewModel.getStandardIllustrationUrl(Difficulty.EZ))
    }

    private fun createViewModel(
        songId: String,
        repository: FakePhigrosRepository = FakePhigrosRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        illustrationUriResolver: IllustrationUriResolver = IllustrationUriResolver(
            NoOpStandardArtworkCache,
            IllustrationProvider()
        ),
        assetReader: TextAssetReader = TestAssets
    ): SongDetailViewModel = SongDetailViewModel(
        songId = songId,
        initialDifficulty = Difficulty.IN,
        repository = repository,
        settingsRepository = settingsRepository,
        songDataProvider = SongDataProvider(assetReader = assetReader),
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
        assertTrue(state.hasVoted)
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
        assertEquals(UiText.Res(Res.string.song_detail_tags_load_failed), state.error)
        assertTrue(state.categories.isEmpty())
    }

    @Test
    fun staleChartTagLoadCannotOverwriteNewerResult() = runTest(dispatcher) {
        // Given: two overlapping loads; the first one is held back until the
        // second has already started, reproducing the page-entry race where a
        // public (identity-less) fetch competes with the identity refire.
        val settings = apiSettings()
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(chartTagTreeFixture())
            chartTagData = Result.success(chartTagDataFixture())
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        val firstGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        repository.chartTagsGate = firstGate
        viewModel.loadChartTags(Difficulty.IN)
        runCurrent()

        val secondGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        repository.chartTagsGate = secondGate
        viewModel.loadChartTags(Difficulty.IN, markVoteSucceeded = true)
        runCurrent()

        // When: the stale load finishes first...
        firstGate.complete(Unit)
        runCurrent()
        // ...it must be dropped, keeping the newer load's in-flight state.
        assertTrue(viewModel.getChartTagState(Difficulty.IN).isLoading)

        // When: the newer load finishes.
        secondGate.complete(Unit)
        advanceUntilIdle()

        // Then: only the newer result is visible.
        val state = viewModel.getChartTagState(Difficulty.IN)
        assertFalse(state.isLoading)
        assertTrue(state.voteSucceeded)
        assertEquals(listOf("高速", "连打"), state.categories.single { it.name == "配置" }.tags.map { it.name })
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
        assertEquals(UiText.Res(Res.string.song_detail_vote_no_token), state.voteError)
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

    @Test
    fun successfulVotePersistsAccountScopedRecordAndGatesHasVoted() = runTest(dispatcher) {
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

        // Then: the local record is written for the current identity + chart...
        assertEquals(
            listOf("taptap:player-id:api-user:song-a.0:IN"),
            settings.chartVoteKeyWrites
        )
        // ...and the chart reports hasVoted without any server-side isMine.
        val state = viewModel.getChartTagState(Difficulty.IN)
        assertTrue(state.hasVoted)
        assertTrue(state.voteSucceeded)
    }

    @Test
    fun persistedVoteRecordGatesHasVotedWithoutServerTruth() = runTest(dispatcher) {
        // Given
        val settings = apiSettings().apply {
            recordChartVote("taptap:player-id:api-user:song-a.0:IN")
        }
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(chartTagTreeFixture())
            chartTagData = Result.success(chartTagDataFixture())
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.loadChartTags(Difficulty.IN)
        advanceUntilIdle()

        // Then: only the recorded difficulty is gated.
        assertTrue(viewModel.getChartTagState(Difficulty.IN).hasVoted)
        viewModel.loadChartTags(Difficulty.EZ)
        advanceUntilIdle()
        assertFalse(viewModel.getChartTagState(Difficulty.EZ).hasVoted)
    }

    @Test
    fun hasVotedIsTrackedPerDifficultyAfterVoting() = runTest(dispatcher) {
        // Given
        val settings = apiSettings(apiToken = "token-1")
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(chartTagTreeFixture())
            chartTagData = Result.success(chartTagDataFixture())
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When
        viewModel.submitChartTagVote(Difficulty.IN, listOf("高速"), emptyList())
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.getChartTagState(Difficulty.IN).hasVoted)
        assertFalse(viewModel.getChartTagState(Difficulty.EZ).hasVoted)
        assertFalse(viewModel.getChartTagState(Difficulty.AT).hasVoted)
    }

    @Test
    fun voteRecordsAreIsolatedBetweenAccounts() = runTest(dispatcher) {
        // Given: account-a voted this chart in a previous session.
        val settings = apiSettings().apply {
            recordChartVote("taptap:account-a:api-user:song-a.0:IN")
        }
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(chartTagTreeFixture())
            chartTagData = Result.success(chartTagDataFixture())
        }
        val viewModel = createViewModel("song-a.0", repository, settings)
        advanceUntilIdle()

        // When: account-b (the configured identity) loads the same chart.
        viewModel.loadChartTags(Difficulty.IN)
        advanceUntilIdle()

        // Then: account-a's record does not gate account-b.
        assertFalse(viewModel.getChartTagState(Difficulty.IN).hasVoted)

        // When: switching back to account-a and reloading.
        settings.setApiPlatformId("account-a")
        advanceUntilIdle()
        viewModel.loadChartTags(Difficulty.IN)
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.getChartTagState(Difficulty.IN).hasVoted)
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

    private class PerDifficultyAssets(private val songId: String) : TextAssetReader {
        override fun readText(name: String): String = when (name) {
            "info.csv" ->
                "id\tsong\tcomposer\tillustrator\tEZC\tHDC\tINC\tATC\tEZ\tHD\tIN\tAT\n" +
                    "$songId\tHappy Ending\tComposer\tIllustrator" +
                    "\t\t\t\t\t1.0\t2.0\t3.0\t4.0"

            else -> TestAssets.readText(name)
        }
    }

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
