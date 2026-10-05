package org.kasumi321.ushio.phitracker.ui.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.data.song.IllustrationUriResolver
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagCategoryDisplay
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongApiDetail
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.ChartTagApiIdentity
import org.kasumi321.ushio.phitracker.domain.usecase.GetChartTagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.ProposeSongAliasUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.VoteChartTagsUseCase
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.song_detail_alias_propose_failed
import phitracker.composeapp.generated.resources.song_detail_data_load_failed
import phitracker.composeapp.generated.resources.song_detail_tags_load_failed
import phitracker.composeapp.generated.resources.song_detail_vote_failed
import phitracker.composeapp.generated.resources.song_detail_vote_no_token

data class SongApiDetailState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val userRank: Int? = null,
    val totalUsers: Int? = null,
    val avgAcc: Float? = null,
    val avgAccCount: Int? = null,
    val history: List<SongSyncHistoryEntry> = emptyList()
)

data class ChartTagUiState(
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val categories: List<ChartTagCategoryDisplay> = emptyList(),
    val allCategories: List<ChartTagCategoryDisplay> = emptyList(),
    val voteSubmitting: Boolean = false,
    val voteError: UiText? = null,
    val voteSucceeded: Boolean = false,
    val hasVoted: Boolean = false
)

data class AliasProposalUiState(
    val submitting: Boolean = false,
    val error: UiText? = null,
    val succeeded: Boolean = false
)

data class SongDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val songInfo: SongInfo? = null,
    val userRecords: List<BestRecord> = emptyList(),
    val syncHistory: List<SongSyncHistoryEntry> = emptyList(),
    val displayRks: Float = 0f,
    val apiEnabled: Boolean = false,
    val useApiData: Boolean = false,
    val apiUserId: String = "",
    val apiPlatform: String = "",
    val apiPlatformId: String = "",
    val apiToken: String = "",
    val apiDetails: Map<Difficulty, SongApiDetailState> = emptyMap(),
    val chartTags: Map<Difficulty, ChartTagUiState> = emptyMap(),
    val lowIllustrationUrls: Map<Difficulty, String?> = emptyMap(),
    val standardIllustrationUrls: Map<Difficulty, String?> = emptyMap(),
    val chartVoteKeys: Set<String> = emptySet(),
    val aliasProposal: AliasProposalUiState = AliasProposalUiState(),
    val initialDifficulty: Difficulty? = null
)

class SongDetailViewModel(
    private val songId: String,
    initialDifficulty: Difficulty?,
    private val repository: PhigrosRepository,
    private val settingsRepository: SettingsRepository,
    private val songDataProvider: SongDataProvider,
    private val illustrationUriResolver: IllustrationUriResolver,
    private val getChartTagsUseCase: GetChartTagsUseCase,
    private val voteChartTagsUseCase: VoteChartTagsUseCase,
    private val proposeSongAliasUseCase: ProposeSongAliasUseCase
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SongDetailUiState(initialDifficulty = initialDifficulty))
    val uiState: StateFlow<SongDetailUiState> = mutableUiState.asStateFlow()

    // Main-thread confined load counters; see loadChartTags.
    private val chartTagLoadSequences = mutableMapOf<Difficulty, Long>()

    init {
        loadRouteState()
    }

    fun getSongApiDetail(difficulty: Difficulty): SongApiDetailState =
        uiState.value.apiDetails[difficulty] ?: SongApiDetailState()

    fun getChartTagState(difficulty: Difficulty): ChartTagUiState =
        uiState.value.chartTags[difficulty] ?: ChartTagUiState()

    fun getLowIllustrationUrl(difficulty: Difficulty): String? =
        uiState.value.lowIllustrationUrls[difficulty]

    fun getStandardIllustrationUrl(difficulty: Difficulty): String? =
        uiState.value.standardIllustrationUrls[difficulty]

    fun loadChartTags(difficulty: Difficulty, markVoteSucceeded: Boolean = false) {
        val state = uiState.value
        // The chartsTag read endpoints are public; only voting needs the
        // api_token, which submitChartTagVote validates separately.
        if (state.songInfo == null) return
        // Loads overlap on page entry (a public fetch fires before the
        // identity settings arrive, then refires with identity). Only the
        // latest load may write state, otherwise a stale identity-less
        // result can win the race and lose the isMine markers.
        val sequence = (chartTagLoadSequences[difficulty] ?: 0L) + 1L
        chartTagLoadSequences[difficulty] = sequence
        updateChartTags(difficulty) {
            (it ?: ChartTagUiState()).copy(isLoading = true, error = null, voteSucceeded = markVoteSucceeded)
        }
        viewModelScope.launch {
            val identity = ChartTagApiIdentity(
                platform = state.apiPlatform.trim(),
                platformId = state.apiPlatformId.trim(),
                apiUserId = state.apiUserId.trim(),
                apiToken = state.apiToken.trim()
            )
            val result = getChartTagsUseCase(songId, difficulty, identity.takeIf { it.isComplete })
            if (chartTagLoadSequences[difficulty] != sequence) return@launch
            mutableUiState.update { current ->
                current.copy(
                    chartTags = current.chartTags + (
                        difficulty to result.fold(
                            onSuccess = { data ->
                                val serverIsMine = data.all.any { category ->
                                    category.tags.any { it.isMine }
                                }
                                ChartTagUiState(
                                    categories = data.display,
                                    allCategories = data.all,
                                    voteSucceeded = markVoteSucceeded,
                                    hasVoted = serverIsMine || hasLocalVoteRecord(current, difficulty)
                                )
                            },
                            onFailure = {
                                ChartTagUiState(
                                    isLoading = false,
                                    error = UiText.Res(Res.string.song_detail_tags_load_failed)
                                )
                            }
                        )
                        )
                )
            }
        }
    }

    fun submitChartTagVote(difficulty: Difficulty, primaryTags: List<String>, secondaryTags: List<String>) {
        val state = uiState.value
        val token = state.apiToken.trim()
        updateChartTags(difficulty) {
            (it ?: ChartTagUiState()).copy(
                voteSubmitting = true,
                voteError = null,
                voteSucceeded = false
            )
        }
        if (token.isEmpty()) {
            updateChartTags(difficulty) {
                (it ?: ChartTagUiState()).copy(
                    voteSubmitting = false,
                    voteError = UiText.Res(Res.string.song_detail_vote_no_token)
                )
            }
            return
        }
        viewModelScope.launch {
            val result = voteChartTagsUseCase(
                songId = songId,
                difficulty = difficulty,
                primaryTags = primaryTags,
                secondaryTags = secondaryTags,
                identity = ChartTagApiIdentity(
                    platform = state.apiPlatform.trim(),
                    platformId = state.apiPlatformId.trim(),
                    apiUserId = state.apiUserId.trim(),
                    apiToken = token
                )
            )
            result.fold(
                onSuccess = {
                    chartVoteKey(state, difficulty)?.let { settingsRepository.recordChartVote(it) }
                    updateChartTags(difficulty) { (it ?: ChartTagUiState()).copy(hasVoted = true) }
                    loadChartTags(difficulty, markVoteSucceeded = true)
                },
                onFailure = { error ->
                    updateChartTags(difficulty) {
                        (it ?: ChartTagUiState()).copy(
                            voteSubmitting = false,
                            voteError = error.message?.let(UiText::Raw) ?: UiText.Res(Res.string.song_detail_vote_failed)
                        )
                    }
                }
            )
        }
    }

    private fun updateChartTags(difficulty: Difficulty, transform: (ChartTagUiState?) -> ChartTagUiState) {
        mutableUiState.update { state ->
            state.copy(chartTags = state.chartTags + (difficulty to transform(state.chartTags[difficulty])))
        }
    }

    /**
     * Submits a community alias proposal for this song. Authentication rides
     * on the login sessionToken, so no api_token gate here; proposals enter
     * upstream review and never change the local alias list directly.
     */
    fun submitAliasProposal(alias: String, note: String) {
        mutableUiState.update {
            it.copy(aliasProposal = AliasProposalUiState(submitting = true))
        }
        viewModelScope.launch {
            val result = proposeSongAliasUseCase(
                songId = songId,
                alias = alias,
                note = note,
                existingAliases = uiState.value.songInfo?.nicknames.orEmpty()
            )
            mutableUiState.update {
                it.copy(
                    aliasProposal = result.fold(
                        onSuccess = { AliasProposalUiState(succeeded = true) },
                        onFailure = { error ->
                            AliasProposalUiState(
                                error = error.message?.let(UiText::Raw)
                                    ?: UiText.Res(Res.string.song_detail_alias_propose_failed)
                            )
                        }
                    )
                )
            }
        }
    }

    /**
     * Resets the proposal flow when the dialog is (re)opened, so a previous
     * success (which auto-closes the dialog) or failure does not leak into
     * the next attempt.
     */
    fun resetAliasProposalState() {
        mutableUiState.update { it.copy(aliasProposal = AliasProposalUiState()) }
    }

    /**
     * Persisted-vote key for one chart: `{userKey}:{songId}:{difficulty}`,
     * where userKey is the phi-plugin identity triplet
     * `platform:platformId:apiUserId`, so records of different accounts never
     * overlap. Null while the identity is incomplete (no vote can succeed in
     * that state anyway).
     */
    private fun chartVoteKey(state: SongDetailUiState, difficulty: Difficulty): String? {
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        val apiUserId = state.apiUserId.trim()
        if (platform.isEmpty() || platformId.isEmpty() || apiUserId.isEmpty()) return null
        return "$platform:$platformId:$apiUserId:$songId:${difficulty.name}"
    }

    private fun hasLocalVoteRecord(state: SongDetailUiState, difficulty: Difficulty): Boolean {
        val key = chartVoteKey(state, difficulty) ?: return false
        return key in state.chartVoteKeys
    }

    fun loadSongApiDetail(difficulty: Difficulty) {
        val key = currentApiKey(difficulty) ?: return
        mutableUiState.update { state ->
            state.copy(
                apiDetails = state.apiDetails + (
                    difficulty to (state.apiDetails[difficulty] ?: SongApiDetailState()).copy(
                        isLoading = true,
                        error = null
                    )
                )
            )
        }
        viewModelScope.launch {
            val result = repository.getSongApiDetail(key)
            if (currentApiKey(difficulty) != key) return@launch
            mutableUiState.update { state ->
                state.copy(
                    apiDetails = state.apiDetails + (
                        difficulty to result.fold(
                            onSuccess = { detail -> detail.toUiState() },
                            onFailure = {
                                SongApiDetailState(
                                    isLoading = false,
                                    error = UiText.Res(Res.string.song_detail_data_load_failed)
                                )
                            }
                        )
                    )
                )
            }
        }
    }

    private fun loadRouteState() {
        viewModelScope.launch {
            // Thumbnails resolve from the local preload cache only, matching
            // the song cards: never fall back to an on-demand remote download
            // here. The preload also fetches the per-difficulty variants of
            // songs that ship them, so tab switches never hit the network.
            val songInfo = runCatching { songDataProvider.getSongs()[songId] }.getOrNull()
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    notFound = songInfo == null,
                    songInfo = songInfo,
                    lowIllustrationUrls = songInfo?.let { info ->
                        info.difficulties.keys.associateWith { difficulty ->
                            illustrationUriResolver.lowLocalUri(info.id, difficulty)
                        }
                    } ?: emptyMap(),
                    standardIllustrationUrls = songInfo?.let { info ->
                        info.difficulties.keys.associateWith { difficulty ->
                            illustrationUriResolver.standardUri(info.id, difficulty)
                        }
                    } ?: emptyMap()
                )
            }
            if (songInfo == null) return@launch

            launch {
                settingsRepository.chartVoteKeys.collect { keys ->
                    mutableUiState.update { current ->
                        val updated = current.copy(chartVoteKeys = keys)
                        updated.copy(
                            chartTags = updated.chartTags.mapValues { (difficulty, tagState) ->
                                val serverIsMine = tagState.allCategories.any { category ->
                                    category.tags.any { it.isMine }
                                }
                                tagState.copy(hasVoted = serverIsMine || hasLocalVoteRecord(updated, difficulty))
                            }
                        )
                    }
                }
            }

            launch {
                repository.observeSongSyncHistory(songId).collect { history ->
                    mutableUiState.update { it.copy(syncHistory = history) }
                }
            }
            launch {
                val difficulties = songDataProvider.getDifficultyMap()
                val songNames = songDataProvider.getSongNameMap()
                combine(repository.getCachedSave(), repository.getUserProfile()) { save, profile ->
                    val (b30, allRecords) = save?.let {
                        RksCalculator.getB30AndAllRecords(it.gameRecord, difficulties, songNames)
                    } ?: (emptyList<BestRecord>() to emptyList())
                    val displayRks = profile?.rks?.takeIf { it > 0f }
                        ?: RksCalculator.calculateDisplayRks(b30)
                    displayRks to allRecords.filter { it.songId == songId }
                }.collect { (displayRks, records) ->
                    mutableUiState.update {
                        it.copy(
                            displayRks = displayRks,
                            userRecords = records,
                            apiDetails = if (it.displayRks != displayRks) emptyMap() else it.apiDetails
                        )
                    }
                }
            }
            launch {
                combine(
                    combine(
                        settingsRepository.apiEnabled,
                        settingsRepository.useApiData,
                        settingsRepository.apiId,
                        settingsRepository.apiPlatform,
                        settingsRepository.apiPlatformId
                    ) { enabled, useData, apiUserId, platform, platformId ->
                        ApiSettings(enabled, useData, apiUserId, platform, platformId, apiToken = "")
                    },
                    settingsRepository.apiToken
                ) { settings, apiToken -> settings.copy(apiToken = apiToken) }
                    .collect { settings ->
                    mutableUiState.update {
                        val identityChanged =
                            it.apiUserId.trim() != settings.apiUserId.trim() ||
                            it.apiPlatform.trim() != settings.platform.trim() ||
                                it.apiPlatformId.trim() != settings.platformId.trim()
                        val apiOff = !(
                            settings.enabled && settings.useData &&
                                settings.apiUserId.isNotBlank() &&
                                settings.platform.isNotBlank() && settings.platformId.isNotBlank()
                            )
                        it.copy(
                            apiEnabled = settings.enabled,
                            useApiData = settings.useData,
                            apiUserId = settings.apiUserId,
                            apiPlatform = settings.platform,
                            apiPlatformId = settings.platformId,
                            apiToken = settings.apiToken,
                            apiDetails = if (identityChanged || apiOff) emptyMap() else it.apiDetails,
                            // Tag reads are public and independent of the
                            // score-API switch; only an identity change
                            // invalidates them (isMine markers).
                            chartTags = if (identityChanged) emptyMap() else it.chartTags
                        )
                    }
                }
            }
        }
    }

    private fun currentApiKey(difficulty: Difficulty): ApiDetailCacheKey? {
        val state = uiState.value
        if (!state.apiEnabled || !state.useApiData || state.songInfo == null) return null
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        val apiUserId = state.apiUserId.trim()
        val normalizedSongId = songId.trim()
        if (platform.isBlank() || platformId.isBlank() || apiUserId.isBlank() || normalizedSongId.isBlank()) return null
        return ApiDetailCacheKey(
            platform = platform,
            platformId = platformId,
            apiUserId = apiUserId,
            songId = normalizedSongId,
            difficulty = difficulty,
            minRks = (state.displayRks - 0.015f).coerceAtLeast(0f),
            maxRks = state.displayRks + 0.015f
        )
    }

    private data class ApiSettings(
        val enabled: Boolean,
        val useData: Boolean,
        val apiUserId: String,
        val platform: String,
        val platformId: String,
        val apiToken: String
    )

    private fun SongApiDetail.toUiState(): SongApiDetailState = SongApiDetailState(
        userRank = userRank,
        totalUsers = totalUsers,
        avgAcc = avgAcc,
        avgAccCount = avgAccCount,
        history = history
    )
}
