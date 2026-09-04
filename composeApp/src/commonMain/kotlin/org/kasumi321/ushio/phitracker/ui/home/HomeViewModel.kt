package org.kasumi321.ushio.phitracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.AnalyzeB30TagsUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.CheckForUpdateUseCase
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.update.toUpdateCheckState

data class ApiToolResult(
    val isLoading: Boolean = false,
    val message: String? = null,
    val rows: List<ApiToolRow> = emptyList()
)

data class ApiToolRow(
    val label: String,
    val value: String
)

class HomeViewModel(
    private val repository: PhigrosRepository,
    private val getB30UseCase: GetB30UseCase,
    private val getSuggestUseCase: GetSuggestUseCase,
    private val syncSaveUseCase: SyncSaveUseCase,
    private val searchSongUseCase: SearchSongUseCase,
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val tipsProvider: TipsProvider,
    private val settingsRepository: SettingsRepository,
    private val artworkFileCache: StandardArtworkCache = NoOpStandardArtworkCache,
    private val thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader,
    private val checkForUpdateUseCase: CheckForUpdateUseCase = CheckForUpdateUseCase(repository),
    private val appVersionNameProvider: () -> String = { getAppMetadata().versionName },
    private val analyzeB30TagsUseCase: AnalyzeB30TagsUseCase = AnalyzeB30TagsUseCase(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var b30Job: Job? = null
    private var suggestJob: Job? = null
    private var tagAnalysisJob: Job? = null
    private var tagAnalysisKey: String? = null
    private var lastAnalysisRecords: List<BestRecord>? = null

    init {
        loadSongs()
        observeB30()
        observeUserProfile()
        checkIllustrationState()
        viewModelScope.launch {
            songDataProvider.cacheInvalidations.collect {
                loadSongs()
                observeB30()
            }
        }

        // Observe settings flows
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(themeMode = mode)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeColorSource.collect { source ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(colorSource = source)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.seedColorArgb.collect { argb ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(seedColorArgb = argb)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeImageSeedColorArgb.collect { argb ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(imageSeedColorArgb = argb)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeImageUri.collect { uri ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(imageUri = uri)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.paletteStyleName.collect { style ->
                updateB30 { it.copy(themeSettings = it.themeSettings.copy(paletteStyleName = style)) }
            }
        }

        viewModelScope.launch {
            settingsRepository.showB30Overflow.collect { show ->
                updateB30 { it.copy(showB30Overflow = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.overflowCount.collect { count ->
                updateB30 { it.copy(overflowCount = count) }
            }
        }
        viewModelScope.launch {
            settingsRepository.avatarUri.collect { uri ->
                updateProfile { it.copy(avatarUri = uri) }
            }
        }
        viewModelScope.launch {
            settingsRepository.moneyString.collect { money ->
                updateProfile { it.copy(moneyString = money) }
            }
        }
        // Tool tab: observe sync snapshots
        viewModelScope.launch {
            repository.observeSyncSnapshots().collect { list ->
                updateTools { it.copy(syncSnapshots = list) }
            }
        }
        // Tool tab: load sessionToken
        viewModelScope.launch {
            val tokenPair = repository.getSessionToken()
            updateTools { it.copy(sessionToken = tokenPair?.first) }
        }
        // Observe PhiPlugin API settings
        viewModelScope.launch {
            settingsRepository.apiEnabled.collect { enabled ->
                updateTools { it.copy(apiEnabled = enabled) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.useApiData.collect { useApiData ->
                updateTools { it.copy(useApiData = useApiData) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiId.collect { apiUserId ->
                updateTools { it.copy(apiUserId = apiUserId) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiPlatform.collect { platform ->
                updateTools { it.copy(apiPlatform = platform) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiPlatformId.collect { platformId ->
                updateTools { it.copy(apiPlatformId = platformId) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            loadRecentEffectiveSyncHistory()
            loadStats()
        }
        viewModelScope.launch {
            val shouldAutoCheck = settingsRepository.autoCheckUpdate.first()
            if (shouldAutoCheck) {
                checkForUpdate(appVersionNameProvider())
            }
        }
    }

    private fun updateProfile(transform: (ProfileUiState) -> ProfileUiState) {
        _uiState.update { it.copy(profile = transform(it.profile)) }
    }

    private fun updateSongs(transform: (SongsUiState) -> SongsUiState) {
        _uiState.update { it.copy(songs = transform(it.songs)) }
    }

    private fun updateB30(transform: (B30UiState) -> B30UiState) {
        _uiState.update { it.copy(b30 = transform(it.b30)) }
    }

    private fun updateTools(transform: (ToolsUiState) -> ToolsUiState) {
        _uiState.update { it.copy(tools = transform(it.tools)) }
    }

    private fun updateSync(transform: (SyncUiState) -> SyncUiState) {
        _uiState.update { it.copy(sync = transform(it.sync)) }
    }

    private suspend fun loadStats() {
        val clearCounts = repository.getClearCountsByDifficulty().mapKeys { (difficulty, _) -> difficulty.name }
        val fcCount = repository.getTotalFullComboCount()
        val phiCount = repository.getTotalPhiCount()
        updateProfile {
            it.copy(
                clearCounts = clearCounts,
                fcCount = fcCount,
                phiCount = phiCount
            )
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            try {
                val songs = songDataProvider.getSongs().values.toList().sortedBy { it.name }
                val chapters = songs.map { it.chapter }.filter { it.isNotBlank() }.distinct().sorted()
                updateSongs {
                    it.copy(allSongs = songs, filteredSongs = songs, availableChapters = chapters)
                }
                applyFilters()
                AppLogger.event("data", "song_data_loaded", mapOf("count" to songs.size.toString()))
            } catch (e: Exception) {
                AppLogger.event("data", "song_data_load_failed", mapOf("error" to (e.message ?: "unknown")))
            }
        }
    }

    private fun observeB30() {
        b30Job?.cancel()
        b30Job = viewModelScope.launch {
            val diffMap = songDataProvider.getDifficultyMap()
            val nameMap = songDataProvider.getSongNameMap()

            getB30UseCase(diffMap, nameMap)
                .stateIn(viewModelScope, SharingStarted.Eagerly, Pair(emptyList(), emptyList()))
                .collect { (b30, allRecords) ->
                    val computedRks = RksCalculator.calculateDisplayRks(b30)
                    val cachedSave = repository.getCachedSave().first()
                    val suggestResult = cachedSave?.let {
                        buildSuggestItems(
                            currentB30 = b30,
                            records = it.gameRecord,
                            difficulties = diffMap,
                            songNames = nameMap,
                            mode = _uiState.value.tools.suggestTargetMode,
                            input = _uiState.value.tools.suggestTargetInput
                        )
                    } ?: SuggestBuildResult(emptyList(), null)
                    _uiState.update { state ->
                        state.copy(
                            b30 = state.b30.copy(
                            b30 = b30,
                            allRecords = allRecords,
                                displayRks = if (state.b30.displayRks == 0f) computedRks else state.b30.displayRks
                            ),
                            tools = state.tools.copy(
                            suggestItems = suggestResult.items,
                                suggestTargetError = suggestResult.error
                            ),
                            sync = state.sync.copy(isLoading = false)
                        )
                    }
                    refreshTagAnalysis(b30)
                }
        }
    }

    /**
     * Recomputes the B30 chart-tag cluster analysis when the B30 content
     * changes and the API switch is on. Skips duplicate refreshes keyed by
     * the (song, difficulty) list so recomposition does not refetch.
     */
    private fun refreshTagAnalysis(b30: List<BestRecord>) {
        if (!_uiState.value.tools.apiEnabled || b30.isEmpty()) {
            tagAnalysisJob?.cancel()
            tagAnalysisKey = null
            lastAnalysisRecords = null
            updateB30 { it.copy(tagAnalysis = B30TagAnalysisState()) }
            return
        }
        val records = b30.filter { it.isPhi }.take(3) + b30.filter { !it.isPhi }.take(27)
        val key = records.joinToString("\u0000") { "${it.songId}:${it.difficulty.name}" }
        if (key == tagAnalysisKey) return
        tagAnalysisKey = key
        lastAnalysisRecords = records
        tagAnalysisJob?.cancel()
        updateB30 { it.copy(tagAnalysis = it.tagAnalysis.copy(isLoading = true, error = null)) }
        tagAnalysisJob = viewModelScope.launch {
            val result = repository.getB30ChartTags(records)
            if (tagAnalysisKey != key) return@launch
            result.fold(
                onSuccess = { batch ->
                    updateB30 {
                        it.copy(
                            tagAnalysis = B30TagAnalysisState(
                                analysis = analyzeB30TagsUseCase(records, batch)
                            )
                        )
                    }
                },
                onFailure = {
                    updateB30 {
                        it.copy(
                            tagAnalysis = B30TagAnalysisState(
                                error = "标签统计获取失败，请稍后重试"
                            )
                        )
                    }
                }
            )
        }
    }

    fun retryTagAnalysis() {
        tagAnalysisKey = null
        lastAnalysisRecords?.let { refreshTagAnalysis(_uiState.value.b30.b30) }
    }

    private data class SuggestBuildResult(
        val items: List<SuggestItem>,
        val error: String?
    )

    private suspend fun buildSuggestItems(
        currentB30: List<BestRecord>,
        records: Map<String, org.kasumi321.ushio.phitracker.domain.model.SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>,
        mode: SuggestTargetMode,
        input: String
    ): SuggestBuildResult {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) {
            // Sweeping every game chart (and, for the final-RKS mode, binary-searching
            // each) is heavy enough to jank the UI thread, so keep it on Default.
            val items = withContext(Dispatchers.Default) {
                getSuggestUseCase(
                    currentB30 = currentB30,
                    records = records,
                    difficulties = difficulties,
                    songNames = songNames,
                    limit = 30
                )
            }
            return SuggestBuildResult(items = items, error = null)
        }

        val targetInputPattern = Regex("""\d+(\.\d{0,2})?""")
        if (!targetInputPattern.matches(normalizedInput)) {
            return SuggestBuildResult(emptyList(), "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数")
        }

        val targetRks = normalizedInput.toFloatOrNull()
        if (targetRks == null || targetRks !in 0f..17f) {
            return SuggestBuildResult(emptyList(), "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数")
        }

        val items = withContext(Dispatchers.Default) {
            getSuggestUseCase(
                currentB30 = currentB30,
                records = records,
                difficulties = difficulties,
                songNames = songNames,
                targetMode = mode,
                targetRks = targetRks,
                limit = 30
            )
        }
        val error = if (mode == SuggestTargetMode.PlayerDisplayRks && items.isEmpty()) {
            "当前数据下已达到目标，或没有可提升的谱面能帮助达成该目标"
        } else null
        return SuggestBuildResult(items, error)
    }

    fun setSuggestTargetMode(mode: SuggestTargetMode) {
        updateTools { it.copy(suggestTargetMode = mode) }
        recalculateSuggestItems()
    }

    fun setSuggestTargetInput(input: String) {
        val normalized = input.replace('，', '.')
        updateTools { it.copy(suggestTargetInput = normalized) }
        recalculateSuggestItems()
    }

    private fun recalculateSuggestItems() {
        // Target input can change on every keystroke; cancel the in-flight (heavy)
        // recomputation so rapid edits don't pile up overlapping background work.
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            val state = _uiState.value
            val diffMap = songDataProvider.getDifficultyMap()
            val nameMap = songDataProvider.getSongNameMap()
            val cachedSave = repository.getCachedSave().first()
            val result = cachedSave?.let {
                buildSuggestItems(
                    currentB30 = state.b30.b30,
                    records = it.gameRecord,
                    difficulties = diffMap,
                    songNames = nameMap,
                    mode = state.tools.suggestTargetMode,
                    input = state.tools.suggestTargetInput
                )
            } ?: SuggestBuildResult(emptyList(), null)
            updateTools {
                it.copy(
                    suggestItems = result.items,
                    suggestTargetError = result.error
                )
            }
        }
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            repository.getUserProfile().collect { profile ->
                if (profile != null) {
                    _uiState.update { state ->
                        state.copy(
                            profile = state.profile.copy(
                            nickname = profile.nickname,
                            challengeModeRank = profile.challengeModeRank
                            ),
                            b30 = state.b30.copy(
                                displayRks = if (profile.rks > 0f) profile.rks else state.b30.displayRks
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Check both the completion marker and the durable thumbnail set. The old
     * implementation trusted the marker alone even though it only warmed
     * Coil's evictable cache, which made later launches randomly re-download
     * individual illustrations.
     */
    private fun checkIllustrationState() {
        viewModelScope.launch {
            val alreadyDone = settingsRepository.getPreloadDone()
            val songIds = songDataProvider.getSongs().keys
            val thumbnailsPresent = artworkFileCache.hasAllThumbnails(songIds)
            if (alreadyDone && thumbnailsPresent) {
                updateSongs { it.copy(illustrationReady = true) }
            } else {
                AppLogger.event(
                    "cache",
                    "thumbnail_sync_required",
                    mapOf(
                        "completionMarker" to alreadyDone.toString(),
                        "assetsPresent" to thumbnailsPresent.toString(),
                        "songCount" to songIds.size.toString()
                    )
                )
                updateSongs { it.copy(showPreloadDialog = true, illustrationReady = true) }
            }
        }
    }

    /** Download low-res illustrations to persistent storage, then warm Coil for the current UI. */
    fun startPreloadIllustrations() {
        viewModelScope.launch {
            val songs = songDataProvider.getSongs()
            val total = songs.size

            if (total == 0) {
                settingsRepository.setPreloadDone(true)
                updateSongs {
                    it.copy(
                        isPreloading = false,
                        showPreloadDialog = false,
                        illustrationReady = true,
                        preloadProgress = 1f
                    )
                }
                return@launch
            }

            val semaphore = Semaphore(6)
            val mutex = Mutex()

            updateSongs {
                it.copy(isPreloading = true, preloadTotal = total, preloadCompleted = 0, preloadProgress = 0f)
            }

            var completed = 0
            var hasChildError = false

            val jobs = songs.keys.map { songId ->
                launch {
                    semaphore.withPermit {
                        val result = runCatching {
                            val remoteUrl = illustrationProvider.getLowUrl(songId)
                            val localUri = artworkFileCache.getOrDownloadThumbnail(songId, remoteUrl)
                            // Decode once now so a corrupt/unsupported file does not receive
                            // the durable completion marker.
                            thumbnailPreloader.preload(localUri).getOrThrow()
                        }
                        mutex.withLock {
                            if (result.isFailure) hasChildError = true
                            completed++
                            updateSongs {
                                it.copy(
                                    preloadCompleted = completed,
                                    preloadProgress = completed.toFloat() / total
                                )
                            }
                        }
                    }
                }
            }

            jobs.forEach { it.join() }

            val errorMessage = if (hasChildError) {
                "部分曲绘图片未能加载"
            } else {
                val persistResult = runCatching { settingsRepository.setPreloadDone(true) }
                persistResult.exceptionOrNull()?.message
            }

            _uiState.update { state ->
                state.copy(
                    songs = state.songs.copy(
                    isPreloading = false,
                    showPreloadDialog = false,
                        illustrationReady = true
                    ),
                    sync = state.sync.copy(error = errorMessage)
                )
            }
        }
    }

    /**
     * Skip preload — mark as handled, no more dialog, illustrations load on-demand.
     */
    fun dismissPreload() {
        viewModelScope.launch {
            settingsRepository.setPreloadDone(true)
            updateSongs {
                it.copy(showPreloadDialog = false, illustrationReady = true)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            updateSync { it.copy(isSyncing = true, error = null) }
            AppLogger.event("sync", "refresh_started")
            try {
                val tokenPair = repository.getSessionToken()
                if (tokenPair == null) {
                    updateSync { it.copy(isSyncing = false, error = "请先登录后再操作") }
                    return@launch
                }

                val result = syncSaveUseCase(tokenPair.first, tokenPair.second, SyncMode.Refresh)
                if (result.isSuccess) {
                    val syncResult = result.getOrThrow()
                    val save = syncResult.save
                    // Format Data currency
                    val money = save.gameProgress.money.let { m ->
                        if (m.isEmpty()) emptyList() else m
                    }
                    val units = listOf("KiB", "MiB", "GiB", "TiB", "PiB")
                    val moneyStr = money.withIndex()
                        .reversed()
                        .filter { it.value > 0 }
                        .joinToString(" ") { "${it.value}${units.getOrElse(it.index) { "" }}" }
                    settingsRepository.setMoneyString(moneyStr)

                    if (syncResult.snapshotCreated) {
                        _uiState.update { state ->
                            state.copy(
                                sync = state.sync.copy(isSyncing = false),
                                profile = state.profile.copy(lastSyncTime = syncResult.committedAt)
                            )
                        }
                        loadRecentEffectiveSyncHistory()
                        AppLogger.event(
                            "sync",
                            "refresh_success",
                            mapOf(
                                "changedEntries" to syncResult.changedEntryCount.toString(),
                                "displayRks" to _uiState.value.b30.displayRks.toString()
                            )
                        )
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                sync = state.sync.copy(isSyncing = false),
                                profile = state.profile.copy(
                                lastSyncTime = syncResult.committedAt,
                                recentSyncedRecords = emptyList(),
                                lastSyncedRecord = null
                                )
                            )
                        }
                        AppLogger.event("sync", "refresh_success", mapOf("changedEntries" to "0"))
                    }
                    // Refresh stats
                    loadStats()
                    if (_uiState.value.tools.apiEnabled && _uiState.value.tools.useApiData) {
                        refreshApiToolData()
                    }
                } else {
                    updateSync {
                        it.copy(
                            isSyncing = false,
                            error = result.exceptionOrNull()?.message
                        )
                    }
                    AppLogger.event("sync", "refresh_failed", mapOf("error" to (result.exceptionOrNull()?.message ?: "unknown")))
                }
            } catch (e: Exception) {
                updateSync {
                    it.copy(isSyncing = false, error = e.message)
                }
                AppLogger.event("sync", "refresh_failed", mapOf("error" to (e.message ?: "unknown")))
            }
        }
    }

    private suspend fun loadSyncRecordsForSnapshot(snapshotId: Long) {
        val songs = songDataProvider.getSongs()
        val recentHistory = repository.getSyncHistoryForSnapshot(snapshotId)
        val recentRecords = recentHistory.mapNotNull { entry ->
            val difficulty = runCatching { Difficulty.valueOf(entry.difficulty) }.getOrNull()
                ?: return@mapNotNull null
            val song = songs[entry.songId]
            val chartConstant = song?.difficulties?.get(difficulty) ?: 0f
            val rks = RksCalculator.calculateSingleRks(entry.accuracy, chartConstant)
            BestRecord(
                songId = entry.songId,
                songName = song?.name ?: entry.songId,
                difficulty = difficulty,
                score = entry.score,
                accuracy = entry.accuracy,
                isFullCombo = entry.isFullCombo,
                chartConstant = chartConstant,
                rks = rks,
                isPhi = entry.accuracy >= 100f
            )
        }

        updateProfile {
            it.copy(
                recentSyncedRecords = recentRecords,
                lastSyncedRecord = recentRecords.firstOrNull()
            )
        }
    }

    private suspend fun loadRecentEffectiveSyncHistory(limit: Int = 3) {
        val songs = songDataProvider.getSongs()
        val snapshots = repository.getSyncSnapshotsOnce()
        val effectiveSnapshots = mutableListOf<Pair<SyncSnapshot, List<SongSyncHistoryEntry>>>()

        for (snapshot in snapshots) {
            val entries = repository.getSyncHistoryForSnapshot(snapshot.id)
            if (entries.isNotEmpty()) {
                effectiveSnapshots.add(snapshot to entries)
            }
            if (effectiveSnapshots.size >= limit) break
        }

        val recentRecords = effectiveSnapshots.flatMap { (_, entries) -> entries }.mapNotNull { entry ->
            val difficulty = runCatching { Difficulty.valueOf(entry.difficulty) }.getOrNull()
                ?: return@mapNotNull null
            val song = songs[entry.songId]
            val chartConstant = song?.difficulties?.get(difficulty) ?: 0f
            val rks = RksCalculator.calculateSingleRks(entry.accuracy, chartConstant)
            BestRecord(
                songId = entry.songId,
                songName = song?.name ?: entry.songId,
                difficulty = difficulty,
                score = entry.score,
                accuracy = entry.accuracy,
                isFullCombo = entry.isFullCombo,
                chartConstant = chartConstant,
                rks = rks,
                isPhi = entry.accuracy >= 100f
            )
        }

        updateProfile {
            // Home summary uses the first entry of the newest effective sync snapshot,
            // while the history list keeps every entry from the latest three effective snapshots.
            it.copy(
                lastSyncTime = effectiveSnapshots.firstOrNull()?.first?.timestamp,
                recentSyncedRecords = recentRecords,
                lastSyncedRecord = recentRecords.firstOrNull()
            )
        }
    }

    fun searchSongs(query: String) {
        updateSongs { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun toggleChapter(chapter: String) {
        updateSongs { state ->
            val newChapters = state.selectedChapters.toMutableSet().apply {
                if (contains(chapter)) remove(chapter) else add(chapter)
            }
            state.copy(selectedChapters = newChapters)
        }
        applyFilters()
    }

    fun clearChapters() {
        updateSongs {
            it.copy(selectedChapters = emptySet())
        }
        applyFilters()
    }

    fun filterByDifficulty(diff: Difficulty?) {
        updateSongs { it.copy(selectedDifficulty = diff) }
        applyFilters()
    }

    fun filterByLevelRange(min: Int, max: Int) {
        updateSongs { it.copy(minLevel = min, maxLevel = max) }
        applyFilters()
    }

    fun toggleFilterSheet(show: Boolean) {
        updateSongs { it.copy(showFilterSheet = show) }
    }

    fun resetFilters() {
        updateSongs {
            it.copy(
                selectedChapters = emptySet(),
                selectedDifficulty = null,
                minLevel = 1,
                maxLevel = 17
            )
        }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value.songs
        val allSongsMap = songDataProvider.getSongs()
        val searchResults = if (state.searchQuery.isNotBlank()) {
            searchSongUseCase(state.searchQuery, allSongsMap)
        } else {
            state.allSongs
        }

        val chapters = state.selectedChapters
        val diff = state.selectedDifficulty
        val minLvl = state.minLevel.toFloat()
        val maxLvl = state.maxLevel.toFloat() + 0.99f

        val filtered = searchResults.filter { song ->
            val matchesChapter = chapters.isEmpty() || song.chapter in chapters
            val matchesLevelAndDiff = if (diff != null) {
                val cc = song.difficulties[diff]
                cc != null && cc >= minLvl && cc <= maxLvl
            } else {
                song.difficulties.values.any { cc -> cc >= minLvl && cc <= maxLvl }
            }
            matchesChapter && matchesLevelAndDiff
        }

        updateSongs { it.copy(filteredSongs = filtered) }
    }

    fun getLowIllustrationUrl(songId: String): String? {
        return artworkFileCache.getThumbnailIfPresent(songId)
            ?: illustrationProvider.getLowUrl(songId)
    }

    fun getCachedOrStandardIllustrationUri(songId: String): String {
        return artworkFileCache.getStandardIfPresent(songId)
            ?: illustrationProvider.getStandardUrl(songId)
    }

    fun getRandomTip(): String {
        return tipsProvider.getRandomTip()
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearData()
            updateSync { it.copy(isLoggedOut = true) }
        }
    }

    fun clearError() {
        updateSync { it.copy(error = null) }
    }

    fun setAvatarUri(uri: String?) {
        viewModelScope.launch {
            AppLogger.event("settings", "avatar_changed", mapOf("uriPresent" to (uri != null).toString()))
            settingsRepository.setAvatarUri(uri)
        }
    }

    fun fetchApiRankByUser() {
        val state = _uiState.value.tools
        if (!state.apiEnabled || !state.useApiData) return
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        val apiUserId = state.apiUserId.trim()
        if (platform.isBlank() || platformId.isBlank() || apiUserId.isBlank()) {
            updateTools { it.copy(apiRankByUser = ApiToolResult(message = "请先填写平台名称、平台 ID 与 API 用户 ID")) }
            return
        }

        viewModelScope.launch {
            updateTools { it.copy(apiRankByUser = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRankByUser(platform, platformId, apiUserId)
            if (result.isFailure) {
                updateTools {
                    it.copy(
                        apiRankByUser = ApiToolResult(
                            message = "查询未成功，请检查网络或稍后重试"
                        )
                    )
                }
                return@launch
            }
            val json = result.getOrNull()
            val data = json?.get("data")?.asObject()
            val total = data?.get("totDataNum")?.asInt()
            val users = data?.get("users")?.asArray().orEmpty()
            val meObj = data?.get("me")?.asObject()
            val meFromUsers = users.firstOrNull { it.asObject()?.get("me")?.asBoolean() == true }?.asObject()

            val meRank = meObj?.get("rank")?.asInt()
                ?: meObj?.get("index")?.asInt()
                ?: meObj?.get("save")?.asObject()?.get("rank")?.asInt()
                ?: meFromUsers?.get("index")?.asInt()

            val mePlayerId = meObj?.get("save")?.asObject()?.get("saveInfo")?.asObject()?.get("PlayerId")?.asString()
                ?: meObj?.get("save")?.asObject()?.get("PlayerId")?.asString()
                ?: meFromUsers?.get("saveInfo")?.asObject()?.get("PlayerId")?.asString()

            val meRks = meObj?.get("save")?.asObject()?.get("saveInfo")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()
                ?: meObj?.get("save")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()
                ?: meFromUsers?.get("saveInfo")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()

            val msg = buildString {
                append("总人数: ${total ?: "—"}")
                append("  |  我的名次: ${meRank ?: "—"}")
                if (!mePlayerId.isNullOrBlank()) append("  |  玩家: $mePlayerId")
                if (meRks != null) append("  |  RKS: ${formatFourDecimals(meRks)}")
            }
            val rows = buildList {
                if (!mePlayerId.isNullOrBlank()) add(ApiToolRow("玩家昵称", mePlayerId))
                if (meRks != null) add(ApiToolRow("RKS", formatFourDecimals(meRks)))
                if (meRank != null) add(ApiToolRow("我的名次", meRank.toString()))
                add(ApiToolRow("总人数", total?.toString() ?: "—"))
            }
            updateTools { it.copy(apiRankByUser = ApiToolResult(message = msg, rows = rows)) }
        }
    }

    fun fetchApiRankByPosition(position: Int) {
        if (position <= 0) {
            updateTools { it.copy(apiRankByPosition = ApiToolResult(message = "请输入大于 0 的名次")) }
            return
        }
        viewModelScope.launch {
            updateTools { it.copy(apiRankByPosition = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRankByPosition(position)
            if (result.isFailure) {
                updateTools {
                    it.copy(
                        apiRankByPosition = ApiToolResult(
                            message = "查询未成功，请检查网络或稍后重试"
                        )
                    )
                }
                return@launch
            }
            val json = result.getOrNull()
            val data = json?.get("data")?.asObject()
            val users = data?.get("users")?.asArray().orEmpty()
            val userObj = users.firstOrNull { it.asObject()?.get("index")?.asInt() == position }?.asObject()
                ?: users.minByOrNull {
                    kotlin.math.abs((it.asObject()?.get("index")?.asInt() ?: Int.MAX_VALUE) - position)
                }?.asObject()
            val rank = userObj?.get("index")?.asInt()
            val playerId = userObj?.get("saveInfo")?.asObject()?.get("PlayerId")?.asString()
                ?: userObj?.get("gameuser")?.asObject()?.get("PlayerId")?.asString()
            val rks = userObj?.get("saveInfo")?.asObject()?.get("summary")?.asObject()?.get("rankingScore")?.asFloat()
                ?: userObj?.get("gameuser")?.asObject()?.get("rankingScore")?.asFloat()
            val exact = rank == position
            val msg = buildString {
                append("名次: ${rank ?: position}")
                if (!exact && rank != null) append("（最接近请求 ${position}）")
                append("  |  用户: ${playerId ?: "未知"}")
                if (rks != null) append("  |  RKS: ${formatFourDecimals(rks)}")
            }
            val rows = buildList {
                add(ApiToolRow("请求名次", position.toString()))
                add(ApiToolRow("返回名次", rank?.toString() ?: "—"))
                add(ApiToolRow("玩家昵称", playerId ?: "未知"))
                if (rks != null) add(ApiToolRow("RKS", formatFourDecimals(rks)))
                add(ApiToolRow("匹配状态", if (exact) "精确匹配" else "最接近匹配"))
            }
            updateTools { it.copy(apiRankByPosition = ApiToolResult(message = msg, rows = rows)) }
        }
    }

    fun fetchApiRksRankForValue(rks: Float) {
        if (rks <= 0f) {
            updateTools { it.copy(apiRksRankResult = ApiToolResult(message = "请输入有效的 RKS")) }
            return
        }
        viewModelScope.launch {
            updateTools { it.copy(apiRksRankResult = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRksAbove(rks)
            if (result.isFailure) {
                updateTools {
                    it.copy(
                        apiRksRankResult = ApiToolResult(
                            message = "查询未成功，请检查网络或稍后重试"
                        )
                    )
                }
                return@launch
            }
            val dataObj = result.getOrNull()?.get("data")?.asObject()
            val total = dataObj?.get("totNum")?.asInt()
            val rank = dataObj?.get("rksRank")?.asInt()
            updateTools {
                it.copy(
                    apiTotalUsers = total,
                    apiRksRank = rank,
                    apiRksRankResult = ApiToolResult(
                        message = "大于 ${formatFourDecimals(rks)} 的用户数: ${rank ?: "—"} / ${total ?: "—"}",
                        rows = listOf(
                            ApiToolRow("目标 RKS", formatFourDecimals(rks)),
                            ApiToolRow("大于该 RKS 用户数", rank?.toString() ?: "—"),
                            ApiToolRow("总人数", total?.toString() ?: "—")
                        )
                    )
                )
            }
        }
    }

    private fun refreshApiToolData() {
        refreshTagAnalysis(_uiState.value.b30.b30)
        val state = _uiState.value.tools
        if (!state.apiEnabled || !state.useApiData) {
            updateTools {
                it.copy(
                    apiHistorySnapshots = emptyList(),
                    apiRankByUser = ApiToolResult(),
                    apiRankByPosition = ApiToolResult(),
                    apiRksRankResult = ApiToolResult()
                )
            }
            return
        }

        fetchApiHistorySnapshots()
        fetchApiRankByUser()
        if (_uiState.value.b30.displayRks > 0f) {
            fetchApiRksRankForValue(_uiState.value.b30.displayRks)
        }
    }

    private fun fetchApiHistorySnapshots() {
        val state = _uiState.value.tools
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        val apiUserId = state.apiUserId.trim()
        if (platform.isBlank() || platformId.isBlank() || apiUserId.isBlank()) return

        viewModelScope.launch {
            val result = repository.apiGetSaveHistory(platform, platformId, apiUserId, listOf("rks"))
            if (result.isFailure) {
                return@launch
            }

            val rksArray = result.getOrNull()?.get("data")?.asObject()?.get("rks")?.asArray().orEmpty()
            val snapshots = rksArray.mapIndexedNotNull { index, item ->
                val obj = item.asObject() ?: return@mapIndexedNotNull null
                val date = obj.get("date")?.asString() ?: return@mapIndexedNotNull null
                val value = obj.get("value")?.asFloat() ?: return@mapIndexedNotNull null
                SyncSnapshot(
                    id = index.toLong() + 1L,
                    timestamp = parseIsoToEpoch(date),
                    rks = value,
                    nickname = _uiState.value.profile.nickname,
                    dataCount = 0,
                    lastSyncedSongId = null,
                    lastSyncedDifficulty = null,
                    lastSyncedScore = null,
                    lastSyncedAccuracy = null
                )
            }.sortedBy { it.timestamp }

            updateTools { it.copy(apiHistorySnapshots = snapshots) }
        }
    }

    // --- JSON parsing helpers ---

    private fun parseIsoToEpoch(iso: String): Long {
        return runCatching {
            Instant.parse(iso).toEpochMilliseconds()
        }.getOrElse { Clock.System.now().toEpochMilliseconds() }
    }

    private fun formatFourDecimals(value: Float): String {
        val scaled = (value * 10_000f).roundToLong()
        val whole = scaled / 10_000L
        val fraction = (scaled % 10_000L).toString().padStart(4, '0')
        return "$whole.$fraction"
    }

    // --- Extension helpers for JSON navigation ---
    private fun JsonObject?.get(key: String): JsonElement? = this?.get(key)
    private fun JsonElement?.asObject(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()
    private fun JsonElement?.asArray(): JsonArray? = runCatching { this?.jsonArray }.getOrNull()
    private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.contentOrNull
    private fun JsonElement?.asInt(): Int? = this?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    private fun JsonElement?.asFloat(): Float? = this?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
    private fun JsonElement?.asBoolean(): Boolean? = this?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    // --- KMP-compatible current time millis ---
    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

    // --- Application update check ---

    fun checkForUpdate(currentVersionName: String) {
        viewModelScope.launch {
            updateSync { it.copy(updateCheckState = UpdateCheckState.Checking) }
            AppLogger.event("update", "check_started")
            val result = checkForUpdateUseCase(
                currentVersionName = currentVersionName,
                includePreRelease = settingsRepository.includePreRelease.first()
            ).toUpdateCheckState()
            updateSync { it.copy(updateCheckState = result) }
            when (result) {
                is UpdateCheckState.Available -> AppLogger.event(
                    "update", "check_update_available", mapOf("version" to result.version)
                )
                is UpdateCheckState.Error -> AppLogger.event(
                    "update", "check_failed", mapOf("error" to result.message)
                )
                UpdateCheckState.NoUpdate -> AppLogger.event("update", "check_no_update")
                UpdateCheckState.Checking, UpdateCheckState.Idle -> Unit
            }
        }
    }

    fun dismissUpdateResult() {
        updateSync { it.copy(updateCheckState = UpdateCheckState.Idle) }
    }

}
