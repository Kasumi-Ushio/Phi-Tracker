package org.kasumi321.ushio.phitracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryDao
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotDao
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.NoOpStandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.clearAllImageCache
import org.kasumi321.ushio.phitracker.data.platform.clearImageCacheUrls
import org.kasumi321.ushio.phitracker.data.platform.getAppMetadata
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.data.platform.triggerAppRestart
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestTargetMode
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class Available(val version: String, val htmlUrl: String, val body: String) : UpdateCheckState()
    data object NoUpdate : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

data class ApiToolResult(
    val isLoading: Boolean = false,
    val message: String? = null,
    val rows: List<ApiToolRow> = emptyList()
)

data class ApiToolRow(
    val label: String,
    val value: String
)

data class SongApiDetailState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userRank: Int? = null,
    val totalUsers: Int? = null,
    val avgAcc: Float? = null,
    val avgAccCount: Int? = null,
    val fittedDifficulty: Float? = null,
    val history: List<SongSyncHistoryEntity> = emptyList()
)

data class HomeUiState(
    val b30: List<BestRecord> = emptyList(),
    val displayRks: Float = 0f,
    val nickname: String = "",
    val challengeModeRank: Int = 0,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
    val isLoggedOut: Boolean = false,
    val searchQuery: String = "",
    val filteredSongs: List<SongInfo> = emptyList(),
    val allSongs: List<SongInfo> = emptyList(),
    val allRecords: List<BestRecord> = emptyList(),
    val availableChapters: List<String> = emptyList(),
    val selectedChapters: Set<String> = emptySet(),
    val selectedDifficulty: Difficulty? = null,
    val minLevel: Int = 1,
    val maxLevel: Int = 17,
    val showFilterSheet: Boolean = false,
    // Illustration preload. This must not block home rendering.
    val illustrationReady: Boolean = true,
    val showPreloadDialog: Boolean = false,
    val preloadProgress: Float = 0f,
    val preloadTotal: Int = 0,
    val preloadCompleted: Int = 0,
    val isPreloading: Boolean = false,

    // Settings
    val themeMode: Int = 0,
    val themeColorSource: String = "system",
    val seedColorArgb: Int = -10011977,
    val themeImageSeedColorArgb: Int? = null,
    val themeImageUri: String? = null,
    val paletteStyleName: String = "TonalSpot",
    val showB30Overflow: Boolean = false,
    val overflowCount: Int = 9,

    // Personal home
    val avatarUri: String? = null,
    val lastSyncTime: Long? = null,
    val lastSyncedRecord: BestRecord? = null,
    val recentSyncedRecords: List<BestRecord> = emptyList(),
    val moneyString: String = "",
    val clearCounts: Map<String, Int> = emptyMap(),
    val fcCount: Int = 0,
    val phiCount: Int = 0,

    // Song data update
    val isUpdatingData: Boolean = false,
    val updateDataProgress: Int = 0,
    val updateDataTotal: Int = 0,
    val updateDataFileName: String = "",
    val updateDataError: String? = null,
    val isCachingB30Artwork: Boolean = false,
    val b30ArtworkCacheTotal: Int = 0,
    val b30ArtworkCacheCompleted: Int = 0,
    val b30ArtworkCacheError: String? = null,

    // Tool tab (sync snapshots)
    val syncSnapshots: List<SyncSnapshotEntity> = emptyList(),
    val sessionToken: String? = null,

    // Pre-release channel and update check
    val includePreRelease: Boolean = false,
    val autoCheckUpdate: Boolean = true,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,

    // PhiPlugin API
    val apiEnabled: Boolean = false,
    val useApiData: Boolean = false,
    val apiPlatform: String = "",
    val apiPlatformId: String = "",
    val isApiTesting: Boolean = false,
    val apiTestMessage: String? = null,
    val apiRksRank: Int? = null,
    val apiTotalUsers: Int? = null,
    val apiHistorySnapshots: List<SyncSnapshotEntity> = emptyList(),
    val apiRankByUser: ApiToolResult = ApiToolResult(),
    val apiRankByPosition: ApiToolResult = ApiToolResult(),
    val apiRksRankResult: ApiToolResult = ApiToolResult(),
    val suggestTargetMode: SuggestTargetMode = SuggestTargetMode.PlayerDisplayRks,
    val suggestTargetInput: String = "",
    val suggestTargetError: String? = null,
    val suggestItems: List<SuggestItem> = emptyList(),
    val songApiDetailMap: Map<String, SongApiDetailState> = emptyMap(),

    val crashNotificationGuideShown: Boolean = false
) {
    val themeSettings: PhiTrackerThemeSettings
        get() = PhiTrackerThemeSettings(
            themeMode = themeMode,
            colorSource = themeColorSource,
            seedColorArgb = seedColorArgb,
            imageSeedColorArgb = themeImageSeedColorArgb,
            imageUri = themeImageUri,
            paletteStyleName = paletteStyleName
        )
}

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
    private val clearCacheUrlsFn: suspend (List<String>) -> Unit = ::clearImageCacheUrls,
    private val clearAllCacheFn: suspend () -> Unit = ::clearAllImageCache,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val recordDao: RecordDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val songDataUpdater: SongDataUpdater,
    private val runtimeLogExporter: RuntimeLogExporter,
    private val crashReportExporter: CrashReportExporter,
    private val appVersionNameProvider: () -> String = { getAppMetadata().versionName },
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var b30Job: Job? = null

    init {
        loadSongs()
        observeB30()
        observeUserProfile()
        checkIllustrationState()

        // Observe settings flows
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeColorSource.collect { source ->
                _uiState.update { it.copy(themeColorSource = source) }
            }
        }
        viewModelScope.launch {
            settingsRepository.seedColorArgb.collect { argb ->
                _uiState.update { it.copy(seedColorArgb = argb) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeImageSeedColorArgb.collect { argb ->
                _uiState.update { it.copy(themeImageSeedColorArgb = argb) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeImageUri.collect { uri ->
                _uiState.update { it.copy(themeImageUri = uri) }
            }
        }
        viewModelScope.launch {
            settingsRepository.paletteStyleName.collect { style ->
                _uiState.update { it.copy(paletteStyleName = style) }
            }
        }

        viewModelScope.launch {
            settingsRepository.showB30Overflow.collect { show ->
                _uiState.update { it.copy(showB30Overflow = show) }
            }
        }
        viewModelScope.launch {
            settingsRepository.overflowCount.collect { count ->
                _uiState.update { it.copy(overflowCount = count) }
            }
        }
        viewModelScope.launch {
            settingsRepository.avatarUri.collect { uri ->
                _uiState.update { it.copy(avatarUri = uri) }
            }
        }
        viewModelScope.launch {
            settingsRepository.moneyString.collect { money ->
                _uiState.update { it.copy(moneyString = money) }
            }
        }
        // Tool tab: observe sync snapshots
        viewModelScope.launch {
            syncSnapshotDao.getAll().collect { list ->
                _uiState.update { it.copy(syncSnapshots = list) }
            }
        }
        // Tool tab: load sessionToken
        viewModelScope.launch {
            val tokenPair = repository.getSessionToken()
            _uiState.update { it.copy(sessionToken = tokenPair?.first) }
        }
        // Observe pre-release channel setting
        viewModelScope.launch {
            settingsRepository.includePreRelease.collect { enabled ->
                _uiState.update { it.copy(includePreRelease = enabled) }
            }
        }
        // Observe auto-update check setting
        viewModelScope.launch {
            settingsRepository.autoCheckUpdate.collect { enabled ->
                _uiState.update { it.copy(autoCheckUpdate = enabled) }
            }
        }
        // Observe PhiPlugin API settings
        viewModelScope.launch {
            settingsRepository.apiEnabled.collect { enabled ->
                _uiState.update { it.copy(apiEnabled = enabled) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.useApiData.collect { useApiData ->
                _uiState.update { it.copy(useApiData = useApiData) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiPlatform.collect { platform ->
                _uiState.update { it.copy(apiPlatform = platform) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.apiPlatformId.collect { platformId ->
                _uiState.update { it.copy(apiPlatformId = platformId) }
                refreshApiToolData()
            }
        }
        viewModelScope.launch {
            settingsRepository.crashNotificationGuideShown.collect { shown ->
                _uiState.update { it.copy(crashNotificationGuideShown = shown) }
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

    private suspend fun loadStats() {
        val clearCounts = mapOf(
            "EZ" to recordDao.getClearCountByDifficulty("EZ"),
            "HD" to recordDao.getClearCountByDifficulty("HD"),
            "IN" to recordDao.getClearCountByDifficulty("IN"),
            "AT" to recordDao.getClearCountByDifficulty("AT")
        )
        val fcCount = recordDao.getTotalFcCount()
        val phiCount = recordDao.getTotalPhiCount()
        _uiState.update {
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
                _uiState.update {
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
                            mode = _uiState.value.suggestTargetMode,
                            input = _uiState.value.suggestTargetInput
                        )
                    } ?: SuggestBuildResult(emptyList(), null)
                    _uiState.update {
                        it.copy(
                            b30 = b30,
                            allRecords = allRecords,
                            displayRks = if (it.displayRks == 0f) computedRks else it.displayRks,
                            suggestItems = suggestResult.items,
                            suggestTargetError = suggestResult.error,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private data class SuggestBuildResult(
        val items: List<SuggestItem>,
        val error: String?
    )

    private fun buildSuggestItems(
        currentB30: List<BestRecord>,
        records: Map<String, org.kasumi321.ushio.phitracker.domain.model.SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>,
        mode: SuggestTargetMode,
        input: String
    ): SuggestBuildResult {
        val normalizedInput = input.trim()
        if (normalizedInput.isEmpty()) {
            return SuggestBuildResult(
                items = getSuggestUseCase(
                    currentB30 = currentB30,
                    records = records,
                    difficulties = difficulties,
                    songNames = songNames,
                    limit = 30
                ),
                error = null
            )
        }

        val targetInputPattern = Regex("""\d+(\.\d{0,2})?""")
        if (!targetInputPattern.matches(normalizedInput)) {
            return SuggestBuildResult(emptyList(), "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数")
        }

        val targetRks = normalizedInput.toFloatOrNull()
        if (targetRks == null || targetRks !in 0f..17f) {
            return SuggestBuildResult(emptyList(), "目标 RKS 需要是 0.00 到 17.00 之间的数字，最多两位小数")
        }

        val items = getSuggestUseCase(
            currentB30 = currentB30,
            records = records,
            difficulties = difficulties,
            songNames = songNames,
            targetMode = mode,
            targetRks = targetRks,
            limit = 30
        )
        val error = if (mode == SuggestTargetMode.PlayerDisplayRks && items.isEmpty()) {
            "当前数据下已达到目标，或没有单张谱面可独立推到该目标"
        } else null
        return SuggestBuildResult(items, error)
    }

    fun setSuggestTargetMode(mode: SuggestTargetMode) {
        _uiState.update { it.copy(suggestTargetMode = mode) }
        recalculateSuggestItems()
    }

    fun setSuggestTargetInput(input: String) {
        val normalized = input.replace('，', '.')
        _uiState.update { it.copy(suggestTargetInput = normalized) }
        recalculateSuggestItems()
    }

    private fun recalculateSuggestItems() {
        viewModelScope.launch {
            val state = _uiState.value
            val diffMap = songDataProvider.getDifficultyMap()
            val nameMap = songDataProvider.getSongNameMap()
            val cachedSave = repository.getCachedSave().first()
            val result = cachedSave?.let {
                buildSuggestItems(
                    currentB30 = state.b30,
                    records = it.gameRecord,
                    difficulties = diffMap,
                    songNames = nameMap,
                    mode = state.suggestTargetMode,
                    input = state.suggestTargetInput
                )
            } ?: SuggestBuildResult(emptyList(), null)
            _uiState.update {
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
                            nickname = profile.nickname,
                            displayRks = if (profile.rks > 0f) profile.rks else state.displayRks,
                            challengeModeRank = profile.challengeModeRank
                        )
                    }
                }
            }
        }
    }

    /**
     * Check illustration preload state:
     * - If SharedPreferences records completion → illustrationReady = true directly
     * - Otherwise → show preload dialog without blocking content display
     */
    private fun checkIllustrationState() {
        viewModelScope.launch {
            val alreadyDone = settingsRepository.getPreloadDone()
            if (alreadyDone) {
                _uiState.update { it.copy(illustrationReady = true) }
            } else {
                _uiState.update { it.copy(showPreloadDialog = true, illustrationReady = true) }
            }
        }
    }

    /** Start preload illustrations (low-res versions) by warming the shared Coil cache. */
    fun startPreloadIllustrations() {
        viewModelScope.launch {
            val songs = songDataProvider.getSongs()
            val total = songs.size

            if (total == 0) {
                settingsRepository.setPreloadDone(true)
                _uiState.update {
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

            _uiState.update {
                it.copy(isPreloading = true, preloadTotal = total, preloadCompleted = 0, preloadProgress = 0f)
            }

            var completed = 0
            var hasChildError = false

            val jobs = songs.keys.map { songId ->
                launch {
                    semaphore.withPermit {
                        val result = runCatching { illustrationProvider.getLowUrl(songId) }
                            .mapCatching { url -> thumbnailPreloader.preload(url).getOrThrow() }
                        mutex.withLock {
                            if (result.isFailure) hasChildError = true
                            completed++
                            _uiState.update {
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

            _uiState.update {
                it.copy(
                    isPreloading = false,
                    showPreloadDialog = false,
                    illustrationReady = true,
                    error = errorMessage
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
            _uiState.update {
                it.copy(showPreloadDialog = false, illustrationReady = true)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            AppLogger.event("sync", "refresh_started")
            try {
                val tokenPair = repository.getSessionToken()
                if (tokenPair == null) {
                    _uiState.update { it.copy(isSyncing = false, error = "请先登录后再操作") }
                    return@launch
                }

                // Snapshot: old records for diff calculation
                val oldRecords = recordDao.getAllRecordsOnce()
                val oldRecordMap = oldRecords.associateBy { "${it.songId}:${it.difficulty}" }

                val result = syncSaveUseCase(tokenPair.first, tokenPair.second)
                if (result.isSuccess) {
                    val save = result.getOrThrow()
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

                    val now = currentTimeMillis()

                    // Calculate sync diff (changed records)
                    val newRecords = recordDao.getAllRecordsOnce()
                    val changedEntries = mutableListOf<SongSyncHistoryEntity>()
                    for (newRec in newRecords) {
                        val key = "${newRec.songId}:${newRec.difficulty}"
                        val oldRec = oldRecordMap[key]
                        if (oldRec == null ||
                            oldRec.score != newRec.score ||
                            oldRec.accuracy != newRec.accuracy
                        ) {
                            changedEntries.add(
                                SongSyncHistoryEntity(
                                    snapshotId = 0,
                                    songId = newRec.songId,
                                    difficulty = newRec.difficulty,
                                    score = newRec.score,
                                    accuracy = newRec.accuracy,
                                    isFullCombo = newRec.isFullCombo,
                                    timestamp = now
                                )
                            )
                        }
                    }

                    if (changedEntries.isNotEmpty()) {
                        val state = _uiState.value
                        val snapshot = SyncSnapshotEntity(
                            timestamp = now,
                            rks = state.displayRks,
                            nickname = state.nickname,
                            dataCount = recordDao.getDistinctSongCount(),
                            lastSyncedSongId = state.b30.firstOrNull()?.songId,
                            lastSyncedDifficulty = state.b30.firstOrNull()?.difficulty?.name,
                            lastSyncedScore = state.b30.firstOrNull()?.score,
                            lastSyncedAccuracy = state.b30.firstOrNull()?.accuracy
                        )
                        val snapshotId = syncSnapshotDao.insertAndGetId(snapshot)
                        val entriesWithSnapshot = changedEntries.map { it.copy(snapshotId = snapshotId) }
                        songSyncHistoryDao.insertAll(entriesWithSnapshot)
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                lastSyncTime = snapshot.timestamp
                            )
                        }
                        loadRecentEffectiveSyncHistory()
                        AppLogger.event("sync", "refresh_success", mapOf("changedEntries" to changedEntries.size.toString(), "displayRks" to state.displayRks.toString()))
                    } else {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                lastSyncTime = now,
                                recentSyncedRecords = emptyList(),
                                lastSyncedRecord = null
                            )
                        }
                        AppLogger.event("sync", "refresh_success", mapOf("changedEntries" to "0"))
                    }
                    // Refresh stats
                    loadStats()
                    if (_uiState.value.apiEnabled && _uiState.value.useApiData) {
                        refreshApiToolData()
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            error = result.exceptionOrNull()?.message
                        )
                    }
                    AppLogger.event("sync", "refresh_failed", mapOf("error" to (result.exceptionOrNull()?.message ?: "unknown")))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSyncing = false, error = e.message)
                }
                AppLogger.event("sync", "refresh_failed", mapOf("error" to (e.message ?: "unknown")))
            }
        }
    }

    fun getSyncHistory(songId: String): Flow<List<SongSyncHistoryEntity>> {
        return songSyncHistoryDao.getBySongId(songId)
    }

    private suspend fun loadSyncRecordsForSnapshot(snapshotId: Long) {
        val songs = songDataProvider.getSongs()
        val recentHistory = songSyncHistoryDao.getBySnapshotId(snapshotId)
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

        _uiState.update {
            it.copy(
                recentSyncedRecords = recentRecords,
                lastSyncedRecord = recentRecords.firstOrNull()
            )
        }
    }

    private suspend fun loadRecentEffectiveSyncHistory(limit: Int = 3) {
        val songs = songDataProvider.getSongs()
        val snapshots = syncSnapshotDao.getAllOnce()
        val effectiveSnapshots = mutableListOf<Pair<SyncSnapshotEntity, List<SongSyncHistoryEntity>>>()

        for (snapshot in snapshots) {
            val entries = songSyncHistoryDao.getBySnapshotId(snapshot.id)
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

        _uiState.update {
            // Home summary uses the first entry of the newest effective sync snapshot,
            // while the history list keeps every entry from the latest three effective snapshots.
            it.copy(
                lastSyncTime = effectiveSnapshots.firstOrNull()?.first?.timestamp,
                recentSyncedRecords = recentRecords,
                lastSyncedRecord = recentRecords.firstOrNull()
            )
        }
    }

    fun updateSongData() {
        if (_uiState.value.isUpdatingData) return
        viewModelScope.launch {
            val totalFiles = SongDataUpdater.FILE_NAMES.size
            val oldSongIds = _uiState.value.allSongs
                .map { it.id }
                .toSet()
                .ifEmpty {
                    runCatching { songDataProvider.getSongs().keys.toSet() }.getOrDefault(emptySet())
                }
            _uiState.update {
                it.copy(
                    isUpdatingData = true,
                    updateDataProgress = 0,
                    updateDataTotal = totalFiles,
                    updateDataFileName = "",
                    updateDataError = null
                )
            }
            AppLogger.event("data", "update_song_data_started", mapOf("totalFiles" to totalFiles.toString()))
            val result = songDataUpdater.updateAll { current, total, fileName ->
                _uiState.update {
                    it.copy(
                        updateDataProgress = current,
                        updateDataTotal = total,
                        updateDataFileName = fileName
                    )
                }
            }
            if (result.isSuccess) {
                val cacheResult = runCatching {
                    val newSongIds = songDataProvider.getSongs().keys.toSet()
                    reconcileSongDataIllustrationCache(oldSongIds, newSongIds)
                }
                _uiState.update {
                    it.copy(
                        isUpdatingData = false,
                        updateDataProgress = totalFiles,
                        updateDataFileName = "",
                        updateDataError = cacheResult.exceptionOrNull()?.message
                    )
                }
                AppLogger.event(
                    "data",
                    "update_song_data_success",
                    mapOf("cacheReconcile" to cacheResult.isSuccess.toString())
                )
                // Reload songs and B30
                loadSongs()
                observeB30()
            } else {
                _uiState.update {
                    it.copy(
                        isUpdatingData = false,
                        updateDataError = result.exceptionOrNull()?.message
                    )
                }
                AppLogger.event("data", "update_song_data_failed", mapOf("error" to (result.exceptionOrNull()?.message ?: "unknown")))
            }
        }
    }

    private suspend fun reconcileSongDataIllustrationCache(
        oldSongIds: Set<String>,
        newSongIds: Set<String>
    ) {
        val added = (newSongIds - oldSongIds).sorted()
        val removed = (oldSongIds - newSongIds).sorted()
        var addedSuccess = 0
        var addedFailure = 0

        if (added.isNotEmpty()) {
            val semaphore = Semaphore(6)
            val mutex = Mutex()
            val jobs = added.map { songId ->
                viewModelScope.launch {
                    semaphore.withPermit {
                        val result = runCatching { illustrationProvider.getLowUrl(songId) }
                            .mapCatching { url -> thumbnailPreloader.preload(url).getOrThrow() }
                        mutex.withLock {
                            if (result.isSuccess) addedSuccess++ else addedFailure++
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        if (removed.isNotEmpty()) {
            val urls = removed.flatMap { songId ->
                listOf(
                    illustrationProvider.getLowUrl(songId),
                    illustrationProvider.getStandardUrl(songId),
                    illustrationProvider.getBlurUrl(songId)
                )
            }
            clearCacheUrlsFn(urls)
            artworkFileCache.clearStandard(removed)
        }

        AppLogger.event(
            "cache",
            "song_data_illustration_reconcile",
            mapOf(
                "added" to added.size.toString(),
                "addedSuccess" to addedSuccess.toString(),
                "addedFailure" to addedFailure.toString(),
                "removed" to removed.size.toString()
            )
        )

        if (addedFailure > 0) {
            throw IllegalStateException("曲目数据已更新，但部分曲绘未能下载，可稍后在设置中重试")
        }
    }

    fun dismissUpdateDataError() {
        _uiState.update { it.copy(updateDataError = null) }
    }

    fun searchSongs(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun toggleChapter(chapter: String) {
        _uiState.update { state ->
            val newChapters = state.selectedChapters.toMutableSet().apply {
                if (contains(chapter)) remove(chapter) else add(chapter)
            }
            state.copy(selectedChapters = newChapters)
        }
        applyFilters()
    }

    fun clearChapters() {
        _uiState.update {
            it.copy(selectedChapters = emptySet())
        }
        applyFilters()
    }

    fun filterByDifficulty(diff: Difficulty?) {
        _uiState.update { it.copy(selectedDifficulty = diff) }
        applyFilters()
    }

    fun filterByLevelRange(min: Int, max: Int) {
        _uiState.update { it.copy(minLevel = min, maxLevel = max) }
        applyFilters()
    }

    fun toggleFilterSheet(show: Boolean) {
        _uiState.update { it.copy(showFilterSheet = show) }
    }

    fun resetFilters() {
        _uiState.update {
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
        val state = _uiState.value
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

        _uiState.update { it.copy(filteredSongs = filtered) }
    }

    fun getLowIllustrationUrl(songId: String): String? {
        return illustrationProvider.getLowUrl(songId)
    }

    fun getStandardIllustrationUrl(songId: String): String {
        return illustrationProvider.getStandardUrl(songId)
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
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // --- Settings ---
    fun setThemeMode(mode: Int) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setThemeColorSource(source: String) {
        viewModelScope.launch {
            AppLogger.event("settings", "theme_color_source_changed", mapOf("source" to source))
            settingsRepository.setThemeColorSource(source)
        }
    }

    fun setSeedColorArgb(argb: Int) {
        viewModelScope.launch {
            AppLogger.event("settings", "theme_seed_color_changed")
            settingsRepository.setSeedColorArgb(argb)
        }
    }

    fun setThemeImageColor(uri: String?, seedColorArgb: Int) {
        viewModelScope.launch {
            AppLogger.event("settings", "theme_image_color_selected", mapOf("uriPresent" to (uri != null).toString()))
            settingsRepository.setThemeImageColor(uri, seedColorArgb)
        }
    }

    fun clearThemeImageColor() {
        viewModelScope.launch {
            AppLogger.event("settings", "theme_image_color_cleared")
            settingsRepository.clearThemeImageColor()
        }
    }

    fun setPaletteStyleName(name: String) {
        viewModelScope.launch {
            AppLogger.event("settings", "palette_style_changed", mapOf("style" to name))
            settingsRepository.setPaletteStyleName(name)
        }
    }

    fun setShowB30Overflow(show: Boolean) {
        viewModelScope.launch {
            AppLogger.event("settings", "b30_overflow_changed", mapOf("enabled" to show.toString()))
            settingsRepository.setShowB30Overflow(show)
        }
    }

    fun setOverflowCount(count: Int) {
        viewModelScope.launch {
            AppLogger.event("settings", "b30_overflow_count_changed", mapOf("count" to count.toString()))
            settingsRepository.setOverflowCount(count)
        }
    }

    fun setAvatarUri(uri: String?) {
        viewModelScope.launch {
            AppLogger.event("settings", "avatar_changed", mapOf("uriPresent" to (uri != null).toString()))
            settingsRepository.setAvatarUri(uri)
        }
    }

    fun setIncludePreRelease(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludePreRelease(enabled) }
    }

    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckUpdate(enabled) }
    }

    fun setApiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            AppLogger.event("settings", "api_enabled_changed", mapOf("enabled" to enabled.toString()))
            settingsRepository.setApiEnabled(enabled)
        }
    }

    fun setUseApiData(useApiData: Boolean) {
        viewModelScope.launch {
            AppLogger.event("settings", "use_api_data_changed", mapOf("enabled" to useApiData.toString()))
            settingsRepository.setUseApiData(useApiData)
        }
    }

    fun setApiPlatform(platform: String) {
        viewModelScope.launch { settingsRepository.setApiPlatform(platform) }
    }

    fun setApiPlatformId(platformId: String) {
        viewModelScope.launch { settingsRepository.setApiPlatformId(platformId) }
    }

    fun setCrashNotificationGuideShown() {
        viewModelScope.launch { settingsRepository.setCrashNotificationGuideShown(true) }
    }

    fun clearHighResCache(onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                artworkFileCache.clearAllStandard()
                val songs = songDataProvider.getSongs()
                clearCacheUrlsFn(songs.keys.map { illustrationProvider.getStandardUrl(it) })
            }
            AppLogger.event(
                "cache",
                if (result.isSuccess) "high_res_clear_success" else "high_res_clear_failed",
                mapOf("error" to (result.exceptionOrNull()?.message ?: ""))
            )
            onComplete(result)
        }
    }

    fun cacheB30StandardArtwork(onComplete: (Result<Unit>) -> Unit = {}) {
        if (_uiState.value.isCachingB30Artwork) return
        viewModelScope.launch {
            val songIds = _uiState.value.b30.map { it.songId }.distinct()
            if (songIds.isEmpty()) {
                val failure = Result.failure<Unit>(IllegalStateException("当前没有可缓存的 B30 曲目"))
                onComplete(failure)
                return@launch
            }

            _uiState.update {
                it.copy(
                    isCachingB30Artwork = true,
                    b30ArtworkCacheTotal = songIds.size,
                    b30ArtworkCacheCompleted = 0,
                    b30ArtworkCacheError = null
                )
            }
            AppLogger.event("cache", "b30_standard_artwork_started", mapOf("count" to songIds.size.toString()))

            val semaphore = Semaphore(4)
            val mutex = Mutex()
            var completed = 0
            var failures = 0
            val jobs = songIds.map { songId ->
                launch {
                    semaphore.withPermit {
                        val result = runCatching {
                            val url = illustrationProvider.getStandardUrl(songId)
                            artworkFileCache.getOrDownloadStandard(songId, url)
                            thumbnailPreloader.preload(url).getOrThrow()
                        }
                        mutex.withLock {
                            if (result.isFailure) failures++
                            completed++
                            _uiState.update {
                                it.copy(b30ArtworkCacheCompleted = completed)
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.join() }

            val result = if (failures == 0) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("$failures 个 B30 高清曲绘缓存失败"))
            }
            _uiState.update {
                it.copy(
                    isCachingB30Artwork = false,
                    b30ArtworkCacheError = result.exceptionOrNull()?.message
                )
            }
            AppLogger.event(
                "cache",
                "b30_standard_artwork_finished",
                mapOf("count" to songIds.size.toString(), "failures" to failures.toString())
            )
            onComplete(result)
        }
    }

    fun resetIllustrationDownloadAndExit() {
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.setPreloadDone(false)
                clearAllCacheFn()
            }
            if (result.isSuccess) {
                AppLogger.event("cache", "redownload_reset_success")
                triggerAppRestart()
            } else {
                val message = result.exceptionOrNull()?.message ?: "未知错误"
                AppLogger.event("cache", "redownload_reset_failed", mapOf("error" to message))
                showPlatformMessage("重新下载曲绘失败: $message")
                _uiState.update { it.copy(error = "重新下载曲绘失败: $message") }
            }
        }
    }

    // --- PhiPlugin API methods ---

    fun testApiConnection() {
        viewModelScope.launch {
            val platform = _uiState.value.apiPlatform.trim()
            val platformId = _uiState.value.apiPlatformId.trim()
            if (platform.isBlank() || platformId.isBlank()) {
                _uiState.update { it.copy(apiTestMessage = "请先填写平台名称与平台 ID") }
                return@launch
            }

            _uiState.update { it.copy(isApiTesting = true, apiTestMessage = null) }
            AppLogger.event("api", "test_started")

            val statusResult = repository.apiTest()
            if (statusResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isApiTesting = false,
                        apiTestMessage = "连接失败：${statusResult.exceptionOrNull()?.message ?: "未知错误"}"
                    )
                }
                AppLogger.event("api", "test_failed", mapOf("error" to (statusResult.exceptionOrNull()?.message ?: "unknown")))
                return@launch
            }

            val bindResult = repository.apiGetBindInfo(platform, platformId)
            _uiState.update {
                it.copy(
                    isApiTesting = false,
                    apiTestMessage = if (bindResult.isSuccess) {
                        "连接正常"
                    } else {
                        "已连接，但账号查询失败：${bindResult.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                )
            }
            val success = bindResult.isSuccess
            AppLogger.event("api", if (success) "test_success" else "test_partial", mapOf("bindSuccess" to success.toString()))
            if (success) {
                refreshApiToolData()
            }
        }
    }

    fun getToolSnapshots(): List<SyncSnapshotEntity> {
        val state = _uiState.value
        return if (state.apiEnabled && state.useApiData) {
            state.apiHistorySnapshots
        } else {
            state.syncSnapshots
        }
    }

    fun fetchApiRankByUser() {
        val state = _uiState.value
        if (!state.apiEnabled || !state.useApiData) return
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        if (platform.isBlank() || platformId.isBlank()) {
            _uiState.update { it.copy(apiRankByUser = ApiToolResult(message = "请先填写平台名称与平台 ID")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(apiRankByUser = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRankByUser(platform, platformId)
            if (result.isFailure) {
                _uiState.update {
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
            _uiState.update { it.copy(apiRankByUser = ApiToolResult(message = msg, rows = rows)) }
        }
    }

    fun fetchApiRankByPosition(position: Int) {
        if (position <= 0) {
            _uiState.update { it.copy(apiRankByPosition = ApiToolResult(message = "请输入大于 0 的名次")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(apiRankByPosition = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRankByPosition(position)
            if (result.isFailure) {
                _uiState.update {
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
            _uiState.update { it.copy(apiRankByPosition = ApiToolResult(message = msg, rows = rows)) }
        }
    }

    fun fetchApiRksRankForValue(rks: Float) {
        if (rks <= 0f) {
            _uiState.update { it.copy(apiRksRankResult = ApiToolResult(message = "请输入有效的 RKS")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(apiRksRankResult = ApiToolResult(isLoading = true)) }
            val result = repository.apiGetRksAbove(rks)
            if (result.isFailure) {
                _uiState.update {
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
            _uiState.update {
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

    fun getSongApiDetail(songId: String, difficulty: Difficulty): SongApiDetailState {
        return _uiState.value.songApiDetailMap["$songId:${difficulty.name}"] ?: SongApiDetailState()
    }

    fun loadSongApiDetail(songId: String, difficulty: Difficulty) {
        val state = _uiState.value
        if (!state.apiEnabled || !state.useApiData) return
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        if (platform.isBlank() || platformId.isBlank()) return

        val key = "$songId:${difficulty.name}"
        viewModelScope.launch {
            _uiState.update {
                val updated = it.songApiDetailMap.toMutableMap()
                updated[key] = (updated[key] ?: SongApiDetailState()).copy(isLoading = true, error = null)
                it.copy(songApiDetailMap = updated)
            }

            val currentRks = _uiState.value.displayRks
            val minRks = (currentRks - 0.015f).coerceAtLeast(0f)
            val maxRks = currentRks + 0.015f

            val rankResult = repository.apiGetRank(platform, platformId, songId, difficulty.name)
            val avgResult = repository.apiGetAvgAcc(songId, difficulty.name, minRks, maxRks)
            val fitResult = repository.apiGetFittedDifficulty(songId, difficulty.name)
            val historyResult = repository.apiGetScoreHistory(platform, platformId, songId, difficulty.name)

            if (rankResult.isFailure || avgResult.isFailure || fitResult.isFailure || historyResult.isFailure) {
                val firstError = listOf(rankResult, avgResult, fitResult, historyResult)
                    .firstOrNull { it.isFailure }?.exceptionOrNull()?.message ?: "未知错误"
                _uiState.update {
                    val updated = it.songApiDetailMap.toMutableMap()
                    updated[key] = (updated[key] ?: SongApiDetailState()).copy(
                        isLoading = false,
                        error = "数据获取失败，请稍后重试"
                    )
                    it.copy(songApiDetailMap = updated)
                }
                return@launch
            }

            val rankData = rankResult.getOrNull()?.get("data")?.asObject()
            val userRank = rankData?.get("userRank")?.asInt()
            val totalUsers = rankData?.get("totDataNum")?.asInt()
            val avgData = avgResult.getOrNull()?.get("data")?.asObject()
            val avgAcc = avgData?.get("accAvg")?.asFloat()
            val avgCount = avgData?.get("count")?.asInt()
            val fitted = parseFittedDifficulty(fitResult.getOrNull(), songId, difficulty.name)
            val historyData = historyResult.getOrNull()?.get("data")
            val apiHistory = parseSongHistory(historyData, songId, difficulty.name)

            _uiState.update {
                val updated = it.songApiDetailMap.toMutableMap()
                updated[key] = SongApiDetailState(
                    isLoading = false,
                    error = null,
                    userRank = userRank,
                    totalUsers = totalUsers,
                    avgAcc = avgAcc,
                    avgAccCount = avgCount,
                    fittedDifficulty = fitted,
                    history = apiHistory
                )
                it.copy(songApiDetailMap = updated)
            }
        }
    }

    private fun refreshApiToolData() {
        val state = _uiState.value
        if (!state.apiEnabled || !state.useApiData) {
            _uiState.update {
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
        if (state.displayRks > 0f) {
            fetchApiRksRankForValue(state.displayRks)
        }
    }

    private fun fetchApiHistorySnapshots() {
        val state = _uiState.value
        val platform = state.apiPlatform.trim()
        val platformId = state.apiPlatformId.trim()
        if (platform.isBlank() || platformId.isBlank()) return

        viewModelScope.launch {
            val result = repository.apiGetSaveHistory(platform, platformId, listOf("rks"))
            if (result.isFailure) {
                _uiState.update {
                    it.copy(apiTestMessage = "API 历史拉取失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
                }
                return@launch
            }

            val rksArray = result.getOrNull()?.get("data")?.asObject()?.get("rks")?.asArray().orEmpty()
            val snapshots = rksArray.mapIndexedNotNull { index, item ->
                val obj = item.asObject() ?: return@mapIndexedNotNull null
                val date = obj.get("date")?.asString() ?: return@mapIndexedNotNull null
                val value = obj.get("value")?.asFloat() ?: return@mapIndexedNotNull null
                SyncSnapshotEntity(
                    id = index.toLong() + 1L,
                    timestamp = parseIsoToEpoch(date),
                    rks = value,
                    nickname = _uiState.value.nickname,
                    dataCount = 0,
                    lastSyncedSongId = null,
                    lastSyncedDifficulty = null,
                    lastSyncedScore = null,
                    lastSyncedAccuracy = null
                )
            }.sortedBy { it.timestamp }

            _uiState.update { it.copy(apiHistorySnapshots = snapshots) }
        }
    }

    // --- JSON parsing helpers ---

    private fun parseSongHistory(dataElement: JsonElement?, songId: String, difficulty: String): List<SongSyncHistoryEntity> {
        val directArray = dataElement?.asArray()
        val list = when {
            directArray != null -> parseRecordArray(directArray, songId, difficulty)
            dataElement?.asObject()?.get(difficulty)?.asArray() != null ->
                parseRecordArray(dataElement.asObject()?.get(difficulty)?.asArray().orEmpty(), songId, difficulty)
            else -> emptyList()
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun parseRecordArray(records: JsonArray, songId: String, difficulty: String): List<SongSyncHistoryEntity> {
        return records.mapNotNull { row ->
            val arr = row.asArray() ?: return@mapNotNull null
            if (arr.size < 4) return@mapNotNull null
            val acc = arr.getOrNull(0)?.asFloat() ?: return@mapNotNull null
            val score = arr.getOrNull(1)?.asInt() ?: return@mapNotNull null
            val date = arr.getOrNull(2)?.asString() ?: return@mapNotNull null
            val fc = arr.getOrNull(3)?.asBoolean() ?: false
            SongSyncHistoryEntity(
                snapshotId = 0L,
                songId = songId,
                difficulty = difficulty,
                score = score,
                accuracy = acc,
                isFullCombo = fc,
                timestamp = parseIsoToEpoch(date)
            )
        }
    }

    private fun parseFittedDifficulty(json: JsonObject?, songId: String, difficulty: String): Float? {
        val data = json?.get("data")
        val songMapped = data?.asObject()?.get(songId)?.asObject()?.get(difficulty)?.asFloat()
        if (songMapped != null) return songMapped
        val firstNumber = findFirstNumber(data)
        return firstNumber
    }

    private fun findFirstNumber(element: JsonElement?): Float? {
        when (element) {
            null -> return null
            is JsonPrimitive -> return element.contentOrNull?.toFloatOrNull()
            is JsonObject -> {
                element.values.forEach { value ->
                    val parsed = findFirstNumber(value)
                    if (parsed != null) return parsed
                }
            }
            is JsonArray -> {
                element.forEach { value ->
                    val parsed = findFirstNumber(value)
                    if (parsed != null) return parsed
                }
            }
        }
        return null
    }

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
            _uiState.update { it.copy(updateCheckState = UpdateCheckState.Checking) }
            AppLogger.event("update", "check_started")
            try {
                val includePre = _uiState.value.includePreRelease
                val release = repository.fetchLatestRelease(includePre).getOrThrow()

                val latestVersion = release.tagName.removePrefix("v")

                if (isNewerVersion(latestVersion, currentVersionName)) {
                    _uiState.update {
                        it.copy(
                            updateCheckState = UpdateCheckState.Available(
                                version = release.tagName,
                                htmlUrl = release.htmlUrl,
                                body = release.body ?: "",
                            )
                        )
                    }
                    AppLogger.event("update", "check_update_available", mapOf("version" to release.tagName))
                } else {
                    _uiState.update { it.copy(updateCheckState = UpdateCheckState.NoUpdate) }
                    AppLogger.event("update", "check_no_update")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        updateCheckState = UpdateCheckState.Error(
                            e.message ?: "未知错误"
                        )
                    )
                }
                AppLogger.event("update", "check_failed", mapOf("error" to (e.message ?: "unknown")))
            }
        }
    }

    fun dismissUpdateResult() {
        _uiState.update { it.copy(updateCheckState = UpdateCheckState.Idle) }
    }

    private fun isNewerVersion(newer: String, current: String): Boolean {
        val newerParts = newer.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(newerParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val n = newerParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (n > c) return true
            if (n < c) return false
        }
        return false
    }

    // --- Log export & clear ---

    fun exportRuntimeLogText(): String {
        AppLogger.event("log", "runtime_export")
        return runtimeLogExporter.buildExportText()
    }

    fun hasRuntimeLogs(): Boolean {
        return runtimeLogExporter.hasLogs()
    }

    fun clearRuntimeLogs(): Boolean {
        return try {
            runtimeLogExporter.clearLogs()
            AppLogger.event("log", "runtime_clear", mapOf("status" to "success"))
            true
        } catch (e: Exception) {
            AppLogger.event("log", "runtime_clear", mapOf("status" to "failed", "error" to (e.message ?: "unknown")))
            false
        }
    }

    fun exportCrashLogText(): String {
        AppLogger.event("log", "crash_export")
        return crashReportExporter.buildExportText()
    }

    fun hasCrashLogs(): Boolean {
        return crashReportExporter.hasReports()
    }

    fun clearCrashLogs(): Boolean {
        return try {
            crashReportExporter.clearReports()
            AppLogger.event("log", "crash_clear", mapOf("status" to "success"))
            true
        } catch (e: Exception) {
            AppLogger.event("log", "crash_clear", mapOf("status" to "failed", "error" to (e.message ?: "unknown")))
            false
        }
    }

    fun clearAllLogs(): Boolean {
        val runtimeOk = clearRuntimeLogs()
        val crashOk = clearCrashLogs()
        return runtimeOk && crashOk
    }
}
