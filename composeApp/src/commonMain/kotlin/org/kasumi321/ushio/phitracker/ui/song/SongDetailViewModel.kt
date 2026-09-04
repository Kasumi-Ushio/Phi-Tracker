package org.kasumi321.ushio.phitracker.ui.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.VoteChartTagsUseCase

data class SongApiDetailState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userRank: Int? = null,
    val totalUsers: Int? = null,
    val avgAcc: Float? = null,
    val avgAccCount: Int? = null,
    val history: List<SongSyncHistoryEntry> = emptyList()
)

data class ChartTagUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val categories: List<ChartTagCategoryDisplay> = emptyList(),
    val allCategories: List<ChartTagCategoryDisplay> = emptyList(),
    val voteSubmitting: Boolean = false,
    val voteError: String? = null,
    val voteSucceeded: Boolean = false
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
    val lowIllustrationUrl: String? = null,
    val standardIllustrationUrl: String? = null,
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
    private val voteChartTagsUseCase: VoteChartTagsUseCase
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SongDetailUiState(initialDifficulty = initialDifficulty))
    val uiState: StateFlow<SongDetailUiState> = mutableUiState.asStateFlow()

    init {
        loadRouteState()
    }

    fun getSongApiDetail(difficulty: Difficulty): SongApiDetailState =
        uiState.value.apiDetails[difficulty] ?: SongApiDetailState()

    fun getChartTagState(difficulty: Difficulty): ChartTagUiState =
        uiState.value.chartTags[difficulty] ?: ChartTagUiState()

    fun loadChartTags(difficulty: Difficulty, markVoteSucceeded: Boolean = false) {
        val state = uiState.value
        if (!state.apiEnabled || state.songInfo == null) return
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
            if (uiState.value.chartTags[difficulty]?.isLoading != true) return@launch
            mutableUiState.update { current ->
                current.copy(
                    chartTags = current.chartTags + (
                        difficulty to result.fold(
                            onSuccess = { data ->
                                ChartTagUiState(
                                    categories = data.display,
                                    allCategories = data.all,
                                    voteSucceeded = markVoteSucceeded
                                )
                            },
                            onFailure = {
                                ChartTagUiState(
                                    isLoading = false,
                                    error = "标签数据获取失败，请稍后重试"
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
                    voteError = "缺少 API Token：请先在设置页填写（向任意 Phi-Plugin 机器人发送 /setApiToken <自定义Token> 设置），我们不会上传你的 sessionToken。"
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
                onSuccess = { loadChartTags(difficulty, markVoteSucceeded = true) },
                onFailure = { error ->
                    updateChartTags(difficulty) {
                        (it ?: ChartTagUiState()).copy(
                            voteSubmitting = false,
                            voteError = error.message ?: "投票失败，请稍后重试"
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
                                    error = "数据获取失败，请稍后重试"
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
            val songInfo = runCatching { songDataProvider.getSongs()[songId] }.getOrNull()
            mutableUiState.update {
                it.copy(
                    isLoading = false,
                    notFound = songInfo == null,
                    songInfo = songInfo,
                    lowIllustrationUrl = songInfo?.let { info -> illustrationUriResolver.lowUri(info.id) },
                    standardIllustrationUrl = songInfo?.let { info -> illustrationUriResolver.standardUri(info.id) }
                )
            }
            if (songInfo == null) return@launch

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
                            chartTags = if (identityChanged || !settings.enabled) emptyMap() else it.chartTags
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
