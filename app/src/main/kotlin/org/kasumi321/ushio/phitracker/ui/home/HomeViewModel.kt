package org.kasumi321.ushio.phitracker.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetSuggestUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SuggestItem
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotDao
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryDao
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryEntity
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * 应用更新检查状态
 */
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
    // 曲绘预加载 — 阻塞式流程
    val illustrationReady: Boolean = false,
    val showPreloadDialog: Boolean = false,
    val preloadProgress: Float = 0f,
    val preloadTotal: Int = 0,
    val preloadCompleted: Int = 0,
    val isPreloading: Boolean = false,
    // 设置
    val themeMode: Int = 0,
    val showB30Overflow: Boolean = false,
    val overflowCount: Int = 9,
    // 个人首页
    val avatarUri: String? = null,
    val lastSyncTime: Long? = null,
    val lastSyncedRecord: BestRecord? = null,
    val recentSyncedRecords: List<BestRecord> = emptyList(),
    val moneyString: String = "",
    val clearCounts: Map<String, Int> = emptyMap(),  // EZ/HD/IN/AT -> count
    val fcCount: Int = 0,
    val phiCount: Int = 0,
    // 曲目数据更新
    val isUpdatingData: Boolean = false,
    val updateDataProgress: Int = 0,
    val updateDataTotal: Int = 0,
    val updateDataFileName: String = "",
    val updateDataError: String? = null,
    // 工具 Tab
    val syncSnapshots: List<org.kasumi321.ushio.phitracker.data.database.SyncSnapshotEntity> = emptyList(),
    val sessionToken: String? = null,
    // 应用更新
    val includePreRelease: Boolean = false,
    val autoCheckUpdate: Boolean = true,
    val updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    // 查分 API
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
    val suggestItems: List<SuggestItem> = emptyList(),
    val songApiDetailMap: Map<String, SongApiDetailState> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: PhigrosRepository,
    private val getB30UseCase: GetB30UseCase,
    private val getSuggestUseCase: GetSuggestUseCase,
    private val syncSaveUseCase: SyncSaveUseCase,
    private val searchSongUseCase: SearchSongUseCase,
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val tipsProvider: TipsProvider,
    private val settingsRepository: SettingsRepository,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val recordDao: RecordDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val songDataUpdater: SongDataUpdater
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadSongs()
        observeB30()
        observeUserProfile()
        checkIllustrationState()
        
        // 观察设置流
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
        // 工具 Tab: 观察同步快照列表
        viewModelScope.launch {
            syncSnapshotDao.getAll().collect { list ->
                _uiState.update { it.copy(syncSnapshots = list) }
                loadRecentEffectiveSyncHistory()
            }
        }
        // 工具 Tab: 加载 sessionToken
        viewModelScope.launch {
            val tokenPair = repository.getSessionToken()
            _uiState.update { it.copy(sessionToken = tokenPair?.first) }
        }
        // 观察更新频道设置
        viewModelScope.launch {
            settingsRepository.includePreRelease.collect { enabled ->
                _uiState.update { it.copy(includePreRelease = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoCheckUpdate.collect { enabled ->
                _uiState.update { it.copy(autoCheckUpdate = enabled) }
            }
        }
        viewModelScope.launch {
            if (settingsRepository.autoCheckUpdate.first()) {
                checkForUpdate()
            }
        }
        // 观察查分 API 设置
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
        // 加载最近同步快照 + 统计数据
        viewModelScope.launch {
            loadRecentEffectiveSyncHistory()
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
        viewModelScope.launch {
            val diffMap = songDataProvider.getDifficultyMap()
            val nameMap = songDataProvider.getSongNameMap()

            getB30UseCase(diffMap, nameMap)
                .stateIn(viewModelScope, SharingStarted.Eagerly, Pair(emptyList(), emptyList()))
                .collect { (b30, allRecords) ->
                    val computedRks = RksCalculator.calculateDisplayRks(b30)
                    val cachedSave = repository.getCachedSave().first()
                    val suggestItems = cachedSave?.let {
                        getSuggestUseCase(
                            currentB30 = b30,
                            records = it.gameRecord,
                            difficulties = diffMap,
                            songNames = nameMap,
                            limit = 30
                        )
                    }.orEmpty()
                    _uiState.update {
                        it.copy(
                            b30 = b30,
                            allRecords = allRecords,
                            displayRks = if (it.displayRks == 0f) computedRks else it.displayRks,
                            suggestItems = suggestItems,
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
     * 检查曲绘预加载状态:
     * - 如果 SharedPreferences 记录了已完成 → 直接 illustrationReady = true
     * - 否则 → 弹出预加载对话框, 阻止内容展示
     */
    private fun checkIllustrationState() {
        viewModelScope.launch {
            val alreadyDone = settingsRepository.getPreloadDone()
            if (alreadyDone) {
                Timber.d("Illustrations already preloaded, skipping dialog")
                _uiState.update { it.copy(illustrationReady = true) }
            } else {
                Timber.d("First launch: showing preload dialog")
                _uiState.update { it.copy(showPreloadDialog = true, illustrationReady = false) }
            }
        }
    }

    /**
     * 开始预加载曲绘 (低清版本)
     */
    fun startPreloadIllustrations() {
        viewModelScope.launch {
            val songs = songDataProvider.getSongs()
            val total = songs.size
            val imageLoader = appContext.imageLoader
            val semaphore = Semaphore(6)

            _uiState.update {
                it.copy(isPreloading = true, preloadTotal = total, preloadCompleted = 0, preloadProgress = 0f)
            }

            Timber.i("Starting preload of %d illustrations", total)
            var completed = 0

            val jobs = songs.keys.map { songId ->
                launch {
                    semaphore.withPermit {
                        try {
                            val url = illustrationProvider.getLowUrl(songId)
                            val request = ImageRequest.Builder(appContext)
                                .data(url)
                                .size(168) // 与 SongsTab 和 ScoreCard 调用的 size 保持绝对一致，以精准命中内存缓存
                                .build()
                            imageLoader.execute(request)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to preload illustration for %s", songId)
                        }
                        synchronized(this@HomeViewModel) {
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

            // 持久化: 标记已完成, 后续启动不再弹窗
            settingsRepository.setPreloadDone(true)

            Timber.i("Preload complete: %d/%d illustrations cached", completed, total)
            _uiState.update {
                it.copy(
                    isPreloading = false,
                    showPreloadDialog = false,
                    illustrationReady = true
                )
            }
        }
    }

    /**
     * 跳过预加载 — 标记已处理, 不再弹窗, 但曲绘会按需加载
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
            val tokenPair = repository.getSessionToken()
            if (tokenPair == null) {
                _uiState.update { it.copy(isSyncing = false, error = "未登录") }
                return@launch
            }

            // 拍快照：同步前的旧记录（用于 diff 计算）
            val oldRecords = recordDao.getAllRecordsOnce()
            val oldRecordMap = oldRecords.associateBy { "${it.songId}:${it.difficulty}" }

            val result = syncSaveUseCase(tokenPair.first, tokenPair.second)
            if (result.isSuccess) {
                val save = result.getOrThrow()
                // 格式化 Data 货币 (参考 phi-plugin b19.js)
                val money = save.gameProgress.money.let { m ->
                    if (m.isEmpty()) emptyList() else m
                }
                val units = listOf("KiB", "MiB", "GiB", "TiB", "PiB")
                val moneyStr = money.withIndex()
                    .reversed()
                    .filter { it.value > 0 }
                    .joinToString(" ") { "${it.value}${units.getOrElse(it.index) { "" }}" }
                settingsRepository.setMoneyString(moneyStr)

                val now = System.currentTimeMillis()

                // 计算同步差异（发生变化的成绩）
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
                    // 仅在有分数或 ACC 变化时写入同步快照
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
                    Timber.i("Recorded %d changed entries for snapshot #%d", entriesWithSnapshot.size, snapshotId)

                    _uiState.update {
                        it.copy(
                            isSyncing = false
                        )
                    }
                    Timber.i("Sync snapshot #%d recorded: rks=%.4f, money=%s", snapshotId, snapshot.rks, moneyStr)
                } else {
                    _uiState.update { it.copy(isSyncing = false) }
                    Timber.i("Sync completed with no score/acc changes; snapshot not recorded")
                }
                // 刷新统计数据
                loadStats()
                if (_uiState.value.apiEnabled && _uiState.value.useApiData) {
                    refreshApiToolData()
                }
                loadRecentEffectiveSyncHistory()
            } else {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun setAvatarUri(uri: android.net.Uri) {
        viewModelScope.launch {
            settingsRepository.setAvatarUri(uri.toString())
        }
    }

    fun getSyncHistory(songId: String): Flow<List<SongSyncHistoryEntity>> {
        return songSyncHistoryDao.getBySongId(songId)
    }

    private suspend fun loadRecentEffectiveSyncHistory(limit: Int = 3) {
        val songs = songDataProvider.getSongs()
        val snapshots = syncSnapshotDao.getAllOnce()
        val effectiveEntries = mutableListOf<Pair<SyncSnapshotEntity, SongSyncHistoryEntity>>()

        for (snapshot in snapshots) {
            val entries = songSyncHistoryDao.getBySnapshotId(snapshot.id)
            if (entries.isNotEmpty()) {
                effectiveEntries += snapshot to entries.first()
            }
            if (effectiveEntries.size >= limit) break
        }

        val recentRecords = effectiveEntries.mapNotNull { (_, entry) ->
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
                lastSyncTime = effectiveEntries.firstOrNull()?.first?.timestamp,
                recentSyncedRecords = recentRecords,
                lastSyncedRecord = recentRecords.firstOrNull()
            )
        }
    }

    fun updateSongData() {
        if (_uiState.value.isUpdatingData) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingData = true, updateDataProgress = 0, updateDataTotal = 4, updateDataFileName = "", updateDataError = null) }
            val result = songDataUpdater.updateSongData { current, total, fileName ->
                _uiState.update { it.copy(updateDataProgress = current, updateDataTotal = total, updateDataFileName = fileName) }
            }
            if (result.isSuccess) {
                _uiState.update { it.copy(isUpdatingData = false) }
                // 重新加载曲目列表和 B30
                loadSongs()
                observeB30()
            } else {
                _uiState.update { it.copy(isUpdatingData = false, updateDataError = result.exceptionOrNull()?.message) }
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

    fun toggleChapter(chapter: String) {
        _uiState.update { state ->
            val current = state.selectedChapters
            val updated = if (chapter in current) current - chapter else current + chapter
            state.copy(selectedChapters = updated)
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

    fun getIllustrationUrl(songId: String): String {
        return illustrationProvider.getLowUrl(songId)
    }

    fun getStandardIllustrationUrl(songId: String): String {
        return illustrationProvider.getStandardUrl(songId)
    }

    fun getRandomTip(): String {
        return tipsProvider.getRandomTip()
    }

    /**
     * 退出登录：同步清除 Token，异步清理 Room
     * 然后将导航回登录页的动作交给调用方执行
     */
    fun logout(onNavigate: () -> Unit) {
        // 1. 同步清除 Token（commit()），确保导航前 token 已删除
        repository.clearTokenSync()
        // 2. 后台清除 Room 数据（不阻塞导航）
        viewModelScope.launch {
            try {
                repository.clearData()  // token 已清除，这里只需清 Room
            } catch (_: Exception) {}
        }
        // 3. 立即触发导航
        onNavigate()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // --- 设置相关 ---
    fun setThemeMode(mode: Int) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setShowB30Overflow(show: Boolean) = viewModelScope.launch { settingsRepository.setShowB30Overflow(show) }
    fun setOverflowCount(count: Int) = viewModelScope.launch { settingsRepository.setOverflowCount(count.coerceIn(1, 30)) }
    fun enableApi() = viewModelScope.launch { settingsRepository.setApiEnabled(true) }
    fun disableApi() = viewModelScope.launch { settingsRepository.setApiEnabled(false) }
    fun setUseApiData(useApiData: Boolean) = viewModelScope.launch { settingsRepository.setUseApiData(useApiData) }
    fun setApiPlatform(platform: String) = viewModelScope.launch { settingsRepository.setApiPlatform(platform) }
    fun setApiPlatformId(platformId: String) = viewModelScope.launch { settingsRepository.setApiPlatformId(platformId) }

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
                if (meRks != null) append("  |  RKS: ${"%.4f".format(meRks)}")
            }
            val rows = buildList {
                if (!mePlayerId.isNullOrBlank()) add(ApiToolRow("玩家昵称", mePlayerId))
                if (meRks != null) add(ApiToolRow("RKS", "%.4f".format(meRks)))
                if (meRank != null) add(ApiToolRow("我的名次", meRank?.toString() ?: "—"))
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
                ?: users.minByOrNull { kotlin.math.abs((it.asObject()?.get("index")?.asInt() ?: Int.MAX_VALUE) - position) }?.asObject()
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
                if (rks != null) append("  |  RKS: ${"%.4f".format(rks)}")
            }
            val rows = buildList {
                add(ApiToolRow("请求名次", position.toString()))
                add(ApiToolRow("返回名次", rank?.toString() ?: "—"))
                add(ApiToolRow("玩家昵称", playerId ?: "未知"))
                if (rks != null) add(ApiToolRow("RKS", "%.4f".format(rks)))
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
                        message = "大于 ${"%.4f".format(rks)} 的用户数: ${rank ?: "—"} / ${total ?: "—"}",
                        rows = listOf(
                            ApiToolRow("目标 RKS", "%.4f".format(rks)),
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
            is kotlinx.serialization.json.JsonPrimitive -> return element.contentOrNull?.toFloatOrNull()
            is kotlinx.serialization.json.JsonObject -> {
                element.values.forEach { value ->
                    val parsed = findFirstNumber(value)
                    if (parsed != null) return parsed
                }
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.forEach { value ->
                    val parsed = findFirstNumber(value)
                    if (parsed != null) return parsed
                }
            }
        }
        return null
    }

    private fun parseIsoToEpoch(iso: String): Long {
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrElse { System.currentTimeMillis() }
    }

    private fun JsonObject?.get(key: String): JsonElement? = this?.get(key)
    private fun JsonElement?.asObject(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()
    private fun JsonElement?.asArray(): JsonArray? = runCatching { this?.jsonArray }.getOrNull()
    private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.contentOrNull
    private fun JsonElement?.asInt(): Int? = this?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    private fun JsonElement?.asFloat(): Float? = this?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
    private fun JsonElement?.asBoolean(): Boolean? = this?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearHighResCache() {
        viewModelScope.launch {
            val imageLoader = appContext.imageLoader
            val diskCache = imageLoader.diskCache
            val memoryCache = imageLoader.memoryCache
            val songs = songDataProvider.getSongs()
            
            var clearedCount = 0
            songs.keys.forEach { songId ->
                val standardUrl = illustrationProvider.getStandardUrl(songId)
                diskCache?.remove(standardUrl)
                memoryCache?.remove(MemoryCache.Key(standardUrl))
                clearedCount++
            }
            Timber.i("Cleared high-res cache for %d songs", clearedCount)
        }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun resetIllustrationDownloadAndExit() {
        viewModelScope.launch {
            settingsRepository.setPreloadDone(false)
            val imageLoader = appContext.imageLoader
            imageLoader.diskCache?.clear()
            imageLoader.memoryCache?.clear()
            
            Timber.i("All illustration cache cleared, exiting app to re-trigger download.")
            kotlin.system.exitProcess(0)
        }
    }

    // --- 应用内检查更新 ---

    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(updateCheckState = UpdateCheckState.Checking) }
            try {
                val includePreRelease = _uiState.value.includePreRelease
                val currentVersion = org.kasumi321.ushio.phitracker.BuildConfig.VERSION_NAME

                val releases = withContext(Dispatchers.IO) {
                    val url = URL("https://api.github.com/repos/Kasumi-Ushio/Ushio-Prober-Phigros/releases")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    try {
                        val responseText = conn.inputStream.bufferedReader().readText()
                        val json = Json { ignoreUnknownKeys = true }
                        json.decodeFromString<List<GitHubRelease>>(responseText)
                    } finally {
                        conn.disconnect()
                    }
                }

                val candidates = if (includePreRelease) releases else releases.filter { !it.prerelease }
                val latest = candidates.firstOrNull()

                if (latest == null) {
                    _uiState.update { it.copy(updateCheckState = UpdateCheckState.NoUpdate) }
                    return@launch
                }

                val latestVersion = latest.tag_name.removePrefix("v")
                if (isNewerVersion(latestVersion, currentVersion)) {
                    _uiState.update {
                        it.copy(updateCheckState = UpdateCheckState.Available(
                            version = latest.tag_name,
                            htmlUrl = latest.html_url,
                            body = latest.body ?: ""
                        ))
                    }
                } else {
                    _uiState.update { it.copy(updateCheckState = UpdateCheckState.NoUpdate) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for update")
                _uiState.update { it.copy(updateCheckState = UpdateCheckState.Error(e.message ?: "未知错误")) }
            }
        }
    }

    fun setIncludePreRelease(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setIncludePreRelease(enabled) }
    }

    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckUpdate(enabled) }
    }

    fun dismissUpdateResult() {
        _uiState.update { it.copy(updateCheckState = UpdateCheckState.Idle) }
    }

    /**
     * 简单版本号比较 (支持 x.y.z[-suffix] 格式)
     * 返回 true 表示 newer 比 current 更新
     */
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
}

@Serializable
private data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val prerelease: Boolean,
    val body: String? = null
)
