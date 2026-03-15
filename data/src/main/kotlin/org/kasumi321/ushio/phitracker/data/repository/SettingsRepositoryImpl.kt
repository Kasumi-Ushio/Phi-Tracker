package org.kasumi321.ushio.phitracker.data.repository

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : SettingsRepository {

    private val prefs = context.getSharedPreferences("phitracker_settings", Context.MODE_PRIVATE)
    private val preloadPrefs = context.getSharedPreferences("illustration_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0))
    override val themeMode: Flow<Int> = _themeMode.asStateFlow()



    private val _showB30Overflow = MutableStateFlow(prefs.getBoolean("show_b30_overflow", false))
    override val showB30Overflow: Flow<Boolean> = _showB30Overflow.asStateFlow()

    private val _overflowCount = MutableStateFlow(prefs.getInt("overflow_count", 9))
    override val overflowCount: Flow<Int> = _overflowCount.asStateFlow()

    override suspend fun setThemeMode(mode: Int) {
        prefs.edit { putInt("theme_mode", mode) }
        _themeMode.value = mode
    }



    override suspend fun setShowB30Overflow(show: Boolean) {
        prefs.edit { putBoolean("show_b30_overflow", show) }
        _showB30Overflow.value = show
    }

    override suspend fun setOverflowCount(count: Int) {
        prefs.edit { putInt("overflow_count", count) }
        _overflowCount.value = count
    }

    override suspend fun getPreloadDone(): Boolean {
        return preloadPrefs.getBoolean("preload_done", false)
    }

    override suspend fun setPreloadDone(done: Boolean) {
        preloadPrefs.edit(commit = true) { putBoolean("preload_done", done) }
    }

    private val _avatarUri = MutableStateFlow(prefs.getString("avatar_uri", null))
    override val avatarUri: Flow<String?> = _avatarUri.asStateFlow()

    override suspend fun setAvatarUri(uri: String?) {
        prefs.edit { putString("avatar_uri", uri) }
        _avatarUri.value = uri
    }

    private val _moneyString = MutableStateFlow(prefs.getString("money_string", "") ?: "")
    override val moneyString: Flow<String> = _moneyString.asStateFlow()

    override suspend fun setMoneyString(money: String) {
        prefs.edit { putString("money_string", money) }
        _moneyString.value = money
    }

    private val _includePreRelease = MutableStateFlow(prefs.getBoolean("include_pre_release", false))
    override val includePreRelease: Flow<Boolean> = _includePreRelease.asStateFlow()

    override suspend fun setIncludePreRelease(enabled: Boolean) {
        prefs.edit { putBoolean("include_pre_release", enabled) }
        _includePreRelease.value = enabled
    }
}
