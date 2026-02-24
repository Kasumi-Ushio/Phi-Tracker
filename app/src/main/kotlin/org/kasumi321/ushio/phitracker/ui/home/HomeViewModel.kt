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
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import timber.log.Timber
import javax.inject.Inject

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
    // 曲绘预加载 — 阻塞式流程
    val illustrationReady: Boolean = false,   // true = 用户已处理预加载 (下载完/跳过), 可以显示内容
    val showPreloadDialog: Boolean = false,
    val preloadProgress: Float = 0f,
    val preloadTotal: Int = 0,
    val preloadCompleted: Int = 0,
    val isPreloading: Boolean = false,
    
    // 设置
    val themeMode: Int = 0,
    val showB30Overflow: Boolean = false,
    val overflowCount: Int = 9
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: PhigrosRepository,
    private val getB30UseCase: GetB30UseCase,
    private val syncSaveUseCase: SyncSaveUseCase,
    private val searchSongUseCase: SearchSongUseCase,
    private val songDataProvider: SongDataProvider,
    private val illustrationProvider: IllustrationProvider,
    private val tipsProvider: TipsProvider,
    private val settingsRepository: SettingsRepository
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

            val result = syncSaveUseCase(tokenPair.first, tokenPair.second)
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
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

    fun getIllustrationUrl(songId: String): String {
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

    // --- 设置相关 ---
    fun setThemeMode(mode: Int) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setShowB30Overflow(show: Boolean) = viewModelScope.launch { settingsRepository.setShowB30Overflow(show) }
    fun setOverflowCount(count: Int) = viewModelScope.launch { settingsRepository.setOverflowCount(count) }

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
}
