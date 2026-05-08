package org.kasumi321.ushio.phitracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kasumi321.ushio.phitracker.data.platform.SecureKeyValueStorage
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository

class SettingsRepositoryImpl(
    private val storage: SecureKeyValueStorage,
    private val preloadStorage: SecureKeyValueStorage
) : SettingsRepository {
    private val themeModeState = MutableStateFlow(storage.getString(KEY_THEME_MODE)?.toIntOrNull() ?: 0)
    override val themeMode: Flow<Int> = themeModeState.asStateFlow()

    private val showB30OverflowState = MutableStateFlow(storage.getString(KEY_SHOW_B30_OVERFLOW)?.toBooleanStrictOrNull() ?: false)
    override val showB30Overflow: Flow<Boolean> = showB30OverflowState.asStateFlow()

    private val overflowCountState = MutableStateFlow(storage.getString(KEY_OVERFLOW_COUNT)?.toIntOrNull() ?: 9)
    override val overflowCount: Flow<Int> = overflowCountState.asStateFlow()

    override suspend fun setThemeMode(mode: Int) {
        storage.putString(KEY_THEME_MODE, mode.toString())
        themeModeState.value = mode
    }

    override suspend fun setShowB30Overflow(show: Boolean) {
        storage.putString(KEY_SHOW_B30_OVERFLOW, show.toString())
        showB30OverflowState.value = show
    }

    override suspend fun setOverflowCount(count: Int) {
        storage.putString(KEY_OVERFLOW_COUNT, count.toString())
        overflowCountState.value = count
    }

    override suspend fun getPreloadDone(): Boolean = preloadStorage.getString(KEY_PRELOAD_DONE)?.toBooleanStrictOrNull() ?: false

    override suspend fun setPreloadDone(done: Boolean) {
        preloadStorage.putString(KEY_PRELOAD_DONE, done.toString())
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SHOW_B30_OVERFLOW = "show_b30_overflow"
        const val KEY_OVERFLOW_COUNT = "overflow_count"
        const val KEY_PRELOAD_DONE = "preload_done"
    }
}
