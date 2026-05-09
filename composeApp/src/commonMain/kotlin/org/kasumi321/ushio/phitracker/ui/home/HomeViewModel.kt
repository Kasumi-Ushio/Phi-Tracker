package org.kasumi321.ushio.phitracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.domain.usecase.RksCalculator
import org.kasumi321.ushio.phitracker.domain.usecase.SearchSongUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase

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
    val overflowCount: Int = 9
)

class HomeViewModel(
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

    /**
     * Start preload illustrations (low-res versions).
     *
     * Phase 4 difference: In beta.1 (Android), this used Coil's ImageLoader.execute() to
     * warm the memory/disk cache. In CMP commonMain, we cannot cleanly access platform-specific
     * Coil ImageLoader. Instead, we track progress through URL enumeration and document this
     * difference. The actual cache warming will happen on-demand via AsyncImage in ScoreCard.
     */
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

            val persistResult = runCatching { settingsRepository.setPreloadDone(true) }

            val errorMessage = when {
                hasChildError -> "部分预览图加载失败"
                persistResult.isFailure -> persistResult.exceptionOrNull()?.message ?: "保存状态失败"
                else -> null
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
            try {
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSyncing = false, error = e.message)
                }
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

    fun getIllustrationUrl(songId: String): String? {
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
    fun setThemeMode(mode: Int) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setShowB30Overflow(show: Boolean) = viewModelScope.launch { settingsRepository.setShowB30Overflow(show) }
    fun setOverflowCount(count: Int) = viewModelScope.launch { settingsRepository.setOverflowCount(count) }
}
