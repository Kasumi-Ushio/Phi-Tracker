package org.kasumi321.ushio.phitracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryDao
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotDao
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.clearAllImageCache
import org.kasumi321.ushio.phitracker.data.platform.clearImageCacheUrls
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
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase

data class ApiToolResult(
    val isLoading: Boolean = false,
    val message: String? = null
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
    val selectedChapter: String? = null,
    val selectedDifficulty: Difficulty? = null,
    val minLevel: Int = 1,
    val maxLevel: Int = 16,
    val showFilterSheet: Boolean = false,
    // Illustration preload — blocking flow
    val illustrationReady: Boolean = false,
    val showPreloadDialog: Boolean = false,
    val preloadProgress: Float = 0f,
    val preloadTotal: Int = 0,
    val preloadCompleted: Int = 0,
    val isPreloading: Boolean = false,

    // Settings
    val themeMode: Int = 0,
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

    // Tool tab (sync snapshots)
    val syncSnapshots: List<SyncSnapshotEntity> = emptyList(),
    val sessionToken: String? = null,

    // Pre-release channel (observed, no update-check implementation yet)
    val includePreRelease: Boolean = false,

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
    val songApiDetailMap: Map<String, SongApiDetailState> = emptyMap()
)

class HomeViewModel(
    private val repository: PhigrosRepository,
    private val getB30UseCase: GetB30UseCase,
    private val syncSaveUseCase: SyncSaveUseCase,
    private val searchSongUseCase: SearchSongUseCase,
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val tipsProvider: TipsProvider,
    private val settingsRepository: SettingsRepository,
    private val thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader,
    private val clearCacheUrlsFn: suspend (List<String>) -> Unit = ::clearImageCacheUrls,
    private val clearAllCacheFn: suspend () -> Unit = ::clearAllImageCache,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val recordDao: RecordDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val songDataUpdater: SongDataUpdater
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
        // Load latest sync snapshot + stats
        viewModelScope.launch {
            val latest = syncSnapshotDao.getLatest()
            if (latest != null) {
                _uiState.update {
                    it.copy(lastSyncTime = latest.timestamp)
                }
                loadSyncRecordsForSnapshot(latest.id)
            } else {
                _uiState.update {
                    it.copy(recentSyncedRecords = emptyList(), lastSyncedRecord = null)
                }
            }
            loadStats()
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
            val songs = songDataProvider.getSongs().values.toList().sortedBy { it.name }
            val chapters = songs.map { it.chapter }.filter { it.isNotBlank() }.distinct().sorted()
            _uiState.update {
                it.copy(allSongs = songs, filteredSongs = songs, availableChapters = chapters)
            }
            applyFilters()
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
                    _uiState.update {
                        it.copy(
                            b30 = b30,
                            allRecords = allRecords,
                            displayRks = if (it.displayRks == 0f) computedRks else it.displayRks,
                            isLoading = false
                        )
                    }
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
     * - Otherwise → show preload dialog, block content display
     */
    private fun checkIllustrationState() {
        viewModelScope.launch {
            val alreadyDone = settingsRepository.getPreloadDone()
            if (alreadyDone) {
                _uiState.update { it.copy(illustrationReady = true) }
            } else {
                _uiState.update { it.copy(showPreloadDialog = true, illustrationReady = false) }
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
                "部分预览图加载失败"
            } else {
                val persistResult = runCatching { settingsRepository.setPreloadDone(true) }
                persistResult.exceptionOrNull()?.message
            }

            _uiState.update {
                it.copy(
                    isPreloading = false,
                    showPreloadDialog = hasChildError,
                    illustrationReady = !hasChildError,
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
            try {
                val tokenPair = repository.getSessionToken()
                if (tokenPair == null) {
                    _uiState.update { it.copy(isSyncing = false, error = "未登录") }
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
                        loadSyncRecordsForSnapshot(snapshotId)
                    } else {
                        _uiState.update {
                            it.copy(
                                isSyncing = false,
                                lastSyncTime = now,
                                recentSyncedRecords = emptyList(),
                                lastSyncedRecord = null
                            )
                        }
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
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSyncing = false, error = e.message)
                }
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

    fun updateSongData() {
        if (_uiState.value.isUpdatingData) return
        viewModelScope.launch {
            val totalFiles = SongDataUpdater.FILE_NAMES.size
            _uiState.update {
                it.copy(
                    isUpdatingData = true,
                    updateDataProgress = 0,
                    updateDataTotal = totalFiles,
                    updateDataFileName = "",
                    updateDataError = null
                )
            }
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
                _uiState.update {
                    it.copy(
                        isUpdatingData = false,
                        updateDataProgress = totalFiles,
                        updateDataFileName = ""
                    )
                }
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
            }
        }
    }

    fun dismissUpdateDataError() {
        _uiState.update { it.copy(updateDataError = null) }
    }

    fun searchSongs(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun filterByChapter(chapter: String?) {
        _uiState.update { it.copy(selectedChapter = chapter) }
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
                selectedChapter = null,
                selectedDifficulty = null,
                minLevel = 1,
                maxLevel = 16
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

        val chapter = state.selectedChapter
        val diff = state.selectedDifficulty
        val minLvl = state.minLevel.toFloat()
        val maxLvl = state.maxLevel.toFloat() + 0.99f

        val filtered = searchResults.filter { song ->
            val matchesChapter = chapter == null || song.chapter == chapter
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

    fun setShowB30Overflow(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowB30Overflow(show) }
    }

    fun setOverflowCount(count: Int) {
        viewModelScope.launch { settingsRepository.setOverflowCount(count) }
    }

    fun setAvatarUri(uri: String?) {
        viewModelScope.launch { settingsRepository.setAvatarUri(uri) }
    }

    fun setIncludePreRelease(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludePreRelease(enabled) }
    }

    fun setApiEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setApiEnabled(enabled) }
    }

    fun setUseApiData(useApiData: Boolean) {
        viewModelScope.launch { settingsRepository.setUseApiData(useApiData) }
    }

    fun setApiPlatform(platform: String) {
        viewModelScope.launch { settingsRepository.setApiPlatform(platform) }
    }

    fun setApiPlatformId(platformId: String) {
        viewModelScope.launch { settingsRepository.setApiPlatformId(platformId) }
    }

    fun clearHighResCache(onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                val songs = songDataProvider.getSongs()
                val urls = songs.keys.map { illustrationProvider.getStandardUrl(it) }
                clearCacheUrlsFn(urls)
            }
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
                triggerAppRestart()
            } else {
                val message = result.exceptionOrNull()?.message ?: "未知错误"
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

            val statusResult = repository.apiTest()
            if (statusResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isApiTesting = false,
                        apiTestMessage = "连接失败：${statusResult.exceptionOrNull()?.message ?: "未知错误"}"
                    )
                }
                return@launch
            }

            val bindResult = repository.apiGetBindInfo(platform, platformId)
            _uiState.update {
                it.copy(
                    isApiTesting = false,
                    apiTestMessage = if (bindResult.isSuccess) {
                        "连接测试成功"
                    } else {
                        "连接可用，但绑定查询失败：${bindResult.exceptionOrNull()?.message ?: "未知错误"}"
                    }
                )
            }
            if (bindResult.isSuccess) {
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
                            message = "查询失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
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
            _uiState.update { it.copy(apiRankByUser = ApiToolResult(message = msg)) }
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
                            message = "查询失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
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
            _uiState.update { it.copy(apiRankByPosition = ApiToolResult(message = msg)) }
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
                            message = "查询失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
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
                        message = "大于 ${formatFourDecimals(rks)} 的用户数: ${rank ?: "—"} / ${total ?: "—"}"
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
                        error = "API 拉取失败：$firstError"
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
}
