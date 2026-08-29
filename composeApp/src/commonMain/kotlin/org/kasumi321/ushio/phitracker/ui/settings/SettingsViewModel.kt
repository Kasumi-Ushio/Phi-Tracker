package org.kasumi321.ushio.phitracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.kasumi321.ushio.phitracker.data.TipsProvider
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.logging.CrashReportExporter
import org.kasumi321.ushio.phitracker.data.logging.RuntimeLogExporter
import org.kasumi321.ushio.phitracker.data.platform.CoilIllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.IllustrationThumbnailPreloader
import org.kasumi321.ushio.phitracker.data.platform.StandardArtworkCache
import org.kasumi321.ushio.phitracker.data.platform.clearAllImageCache
import org.kasumi321.ushio.phitracker.data.platform.clearImageCacheUrls
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.data.song.IllustrationProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.data.song.SongDataUpdater
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.domain.usecase.CheckForUpdateUseCase
import org.kasumi321.ushio.phitracker.domain.usecase.GetB30UseCase
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.update.toUpdateCheckState

class SettingsViewModel(
    internal val repository: PhigrosRepository,
    internal val settingsRepository: SettingsRepository,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    getB30UseCase: GetB30UseCase,
    private val songDataProvider: SongDataProvider,
    private val songDataUpdater: SongDataUpdater,
    private val illustrationProvider: IllustrationProvider,
    private val artworkFileCache: StandardArtworkCache,
    internal val runtimeLogExporter: RuntimeLogExporter,
    internal val crashReportExporter: CrashReportExporter,
    tipsProvider: TipsProvider,
    private val thumbnailPreloader: IllustrationThumbnailPreloader = CoilIllustrationThumbnailPreloader,
    private val clearCacheUrls: suspend (List<String>) -> Unit = ::clearImageCacheUrls,
    private val clearAllCache: suspend () -> Unit = ::clearAllImageCache,
    private val platformMessage: (String) -> Unit = ::showPlatformMessage
) : ViewModel() {
    internal val mutableUiState = MutableStateFlow(
        SettingsUiState(
            hasRuntimeLogs = runtimeLogExporter.hasLogs(),
            hasCrashLogs = crashReportExporter.hasReports(),
            tip = tipsProvider.getRandomTip()
        )
    )
    val uiState: StateFlow<SettingsUiState> = mutableUiState.asStateFlow()
    internal val eventChannel = Channel<SettingsEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var b30: List<BestRecord> = emptyList()

    init {
        observeSettings()
        viewModelScope.launch {
            getB30UseCase(
                songDataProvider.getDifficultyMap(),
                songDataProvider.getSongNameMap()
            ).collect { (records, _) -> b30 = records }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch { settingsRepository.themeMode.collect { value -> mutableUiState.update { it.copy(themeMode = value) } } }
        viewModelScope.launch { settingsRepository.themeColorSource.collect { value -> mutableUiState.update { it.copy(themeColorSource = value) } } }
        viewModelScope.launch { settingsRepository.seedColorArgb.collect { value -> mutableUiState.update { it.copy(seedColorArgb = value) } } }
        viewModelScope.launch { settingsRepository.themeImageSeedColorArgb.collect { value -> mutableUiState.update { it.copy(themeImageSeedColorArgb = value) } } }
        viewModelScope.launch { settingsRepository.themeImageUri.collect { value -> mutableUiState.update { it.copy(themeImageUri = value) } } }
        viewModelScope.launch { settingsRepository.paletteStyleName.collect { value -> mutableUiState.update { it.copy(paletteStyleName = value) } } }
        viewModelScope.launch { settingsRepository.showB30Overflow.collect { value -> mutableUiState.update { it.copy(showB30Overflow = value) } } }
        viewModelScope.launch { settingsRepository.overflowCount.collect { value -> mutableUiState.update { it.copy(overflowCount = value) } } }
        viewModelScope.launch { settingsRepository.apiEnabled.collect { value -> mutableUiState.update { it.copy(apiEnabled = value) } } }
        viewModelScope.launch { settingsRepository.useApiData.collect { value -> mutableUiState.update { it.copy(useApiData = value) } } }
        viewModelScope.launch { settingsRepository.apiPlatform.collect { value -> mutableUiState.update { it.copy(apiPlatform = value) } } }
        viewModelScope.launch { settingsRepository.apiPlatformId.collect { value -> mutableUiState.update { it.copy(apiPlatformId = value) } } }
        viewModelScope.launch { settingsRepository.includePreRelease.collect { value -> mutableUiState.update { it.copy(includePreRelease = value) } } }
        viewModelScope.launch { settingsRepository.autoCheckUpdate.collect { value -> mutableUiState.update { it.copy(autoCheckUpdate = value) } } }
        viewModelScope.launch { settingsRepository.crashNotificationGuideShown.collect { value -> mutableUiState.update { it.copy(crashNotificationGuideShown = value) } } }
    }

    internal fun launchSetting(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun testApiConnection() {
        viewModelScope.launch {
            val platform = mutableUiState.value.apiPlatform.trim()
            val platformId = mutableUiState.value.apiPlatformId.trim()
            if (platform.isBlank() || platformId.isBlank()) {
                mutableUiState.update { it.copy(apiTestMessage = "请先填写平台名称与平台 ID") }
                return@launch
            }
            mutableUiState.update { it.copy(isApiTesting = true, apiTestMessage = null) }
            AppLogger.event("api", "test_started")
            val status = repository.apiTest()
            if (status.isFailure) {
                val message = status.exceptionOrNull()?.message ?: "未知错误"
                mutableUiState.update { it.copy(isApiTesting = false, apiTestMessage = "连接失败：$message") }
                AppLogger.event("api", "test_failed", mapOf("error" to message))
                return@launch
            }
            val bind = repository.apiGetBindInfo(platform, platformId)
            mutableUiState.update {
                it.copy(
                    isApiTesting = false,
                    apiTestMessage = if (bind.isSuccess) "连接正常" else "已连接，但账号查询失败：${bind.exceptionOrNull()?.message ?: "未知错误"}"
                )
            }
            AppLogger.event("api", if (bind.isSuccess) "test_success" else "test_partial", mapOf("bindSuccess" to bind.isSuccess.toString()))
        }
    }

    fun checkForUpdate(currentVersionName: String) {
        viewModelScope.launch {
            mutableUiState.update { it.copy(updateCheckState = UpdateCheckState.Checking) }
            AppLogger.event("update", "check_started")
            val result = checkForUpdateUseCase(
                currentVersionName,
                settingsRepository.includePreRelease.first()
            ).toUpdateCheckState()
            mutableUiState.update { it.copy(updateCheckState = result) }
            when (result) {
                is UpdateCheckState.Available -> AppLogger.event("update", "check_update_available", mapOf("version" to result.version))
                is UpdateCheckState.Error -> AppLogger.event("update", "check_failed", mapOf("error" to result.message))
                UpdateCheckState.NoUpdate -> AppLogger.event("update", "check_no_update")
                UpdateCheckState.Checking, UpdateCheckState.Idle -> Unit
            }
        }
    }

    fun cacheB30StandardArtwork(onComplete: (Result<Unit>) -> Unit = {}) {
        if (mutableUiState.value.isCachingB30Artwork) return
        viewModelScope.launch {
            val songIds = b30.map { it.songId }.distinct()
            if (songIds.isEmpty()) {
                onComplete(Result.failure(IllegalStateException("当前没有可缓存的 B30 曲目")))
                return@launch
            }
            mutableUiState.update { it.copy(isCachingB30Artwork = true, b30ArtworkCacheTotal = songIds.size, b30ArtworkCacheCompleted = 0, b30ArtworkCacheError = null) }
            AppLogger.event("cache", "b30_standard_artwork_started", mapOf("count" to songIds.size.toString()))
            val semaphore = Semaphore(4)
            val mutex = Mutex()
            var completed = 0
            var failures = 0
            songIds.map { songId ->
                launch {
                    semaphore.withPermit {
                        val result = runCatching {
                            val url = illustrationProvider.getStandardUrl(songId)
                            artworkFileCache.getOrDownloadStandard(songId, url)
                            thumbnailPreloader.preload(url).getOrThrow()
                        }
                        mutex.withLock {
                            if (result.isFailure) failures++
                            mutableUiState.update { it.copy(b30ArtworkCacheCompleted = ++completed) }
                        }
                    }
                }
            }.forEach { it.join() }
            val result = if (failures == 0) Result.success(Unit) else Result.failure(IllegalStateException("$failures 个 B30 高清曲绘缓存失败"))
            mutableUiState.update { it.copy(isCachingB30Artwork = false, b30ArtworkCacheError = result.exceptionOrNull()?.message) }
            AppLogger.event("cache", "b30_standard_artwork_finished", mapOf("count" to songIds.size.toString(), "failures" to failures.toString()))
            onComplete(result)
        }
    }

    fun clearHighResCache(onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching {
                artworkFileCache.clearAllStandard()
                clearCacheUrls(songDataProvider.getSongs().keys.map(illustrationProvider::getStandardUrl))
            }
            AppLogger.event("cache", if (result.isSuccess) "high_res_clear_success" else "high_res_clear_failed", mapOf("error" to (result.exceptionOrNull()?.message ?: "")))
            onComplete(result)
        }
    }

    fun resetIllustrationDownload() {
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.setPreloadDone(false)
                clearAllCache()
                artworkFileCache.clearAllThumbnails()
                artworkFileCache.clearAllStandard()
            }
            if (result.isSuccess) {
                AppLogger.event("cache", "redownload_reset_success")
                eventChannel.send(SettingsEvent.RestartRequested)
            } else {
                val message = result.exceptionOrNull()?.message ?: "未知错误"
                AppLogger.event("cache", "redownload_reset_failed", mapOf("error" to message))
                platformMessage("重新下载曲绘失败: $message")
                mutableUiState.update { it.copy(updateDataError = "重新下载曲绘失败: $message") }
            }
        }
    }

    fun updateSongData() {
        if (mutableUiState.value.isUpdatingData) return
        viewModelScope.launch {
            val total = SongDataUpdater.FILE_NAMES.size
            val oldSongIds = songDataProvider.getSongs().keys.toSet()
            mutableUiState.update { it.copy(isUpdatingData = true, updateDataProgress = 0, updateDataTotal = total, updateDataFileName = "", updateDataError = null) }
            AppLogger.event("data", "update_song_data_started", mapOf("totalFiles" to total.toString()))
            val result = songDataUpdater.updateAll { current, count, fileName ->
                mutableUiState.update { it.copy(updateDataProgress = current, updateDataTotal = count, updateDataFileName = fileName) }
            }
            if (result.isFailure) {
                val message = result.exceptionOrNull()?.message
                mutableUiState.update { it.copy(isUpdatingData = false, updateDataError = message) }
                AppLogger.event("data", "update_song_data_failed", mapOf("error" to (message ?: "unknown")))
                return@launch
            }
            val reconcile = runCatching { reconcileSongDataIllustrationCache(oldSongIds, songDataProvider.getSongs().keys.toSet()) }
            mutableUiState.update { it.copy(isUpdatingData = false, updateDataProgress = total, updateDataFileName = "", updateDataError = reconcile.exceptionOrNull()?.message) }
            AppLogger.event("data", "update_song_data_success", mapOf("cacheReconcile" to reconcile.isSuccess.toString()))
        }
    }

    private suspend fun reconcileSongDataIllustrationCache(oldSongIds: Set<String>, newSongIds: Set<String>) {
        val added = (newSongIds - oldSongIds).sorted()
        val removed = (oldSongIds - newSongIds).sorted()
        var failures = 0
        coroutineScope {
            val semaphore = Semaphore(6)
            val mutex = Mutex()
            added.map { songId ->
                launch {
                    semaphore.withPermit {
                        val result = runCatching {
                            val localUri = artworkFileCache.getOrDownloadThumbnail(songId, illustrationProvider.getLowUrl(songId))
                            thumbnailPreloader.preload(localUri).getOrThrow()
                        }
                        mutex.withLock { if (result.isFailure) failures++ }
                    }
                }
            }.forEach { it.join() }
        }
        if (removed.isNotEmpty()) {
            clearCacheUrls(removed.flatMap { listOf(illustrationProvider.getLowUrl(it), illustrationProvider.getStandardUrl(it), illustrationProvider.getBlurUrl(it)) })
            artworkFileCache.clearThumbnails(removed)
            artworkFileCache.clearStandard(removed)
        }
        AppLogger.event("cache", "song_data_illustration_reconcile", mapOf("added" to added.size.toString(), "addedSuccess" to (added.size - failures).toString(), "addedFailure" to failures.toString(), "removed" to removed.size.toString()))
        if (failures > 0) error("曲目数据已更新，但部分曲绘未能下载，可稍后在设置中重试")
    }

}
