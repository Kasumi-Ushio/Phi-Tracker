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
    private companion object {
        private const val PREFS_NAME = "phitracker_settings"
        private const val PRELOAD_PREFS_NAME = "illustration_prefs"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_B30_OVERFLOW = "show_b30_overflow"
        private const val KEY_OVERFLOW_COUNT = "overflow_count"
        private const val KEY_PRELOAD_DONE = "preload_done"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_MONEY_STRING = "money_string"
        private const val KEY_INCLUDE_PRE_RELEASE = "include_pre_release"

        private const val KEY_API_ENABLED = "api_enabled"
        private const val KEY_USE_API_DATA = "use_api_data"
        private const val KEY_API_ID = "api_id"
        private const val KEY_API_PLATFORM = "api_platform"
        private const val KEY_API_PLATFORM_ID = "api_platform_id"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val preloadPrefs = context.getSharedPreferences(PRELOAD_PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getInt(KEY_THEME_MODE, 0))
    override val themeMode: Flow<Int> = _themeMode.asStateFlow()



    private val _showB30Overflow = MutableStateFlow(prefs.getBoolean(KEY_SHOW_B30_OVERFLOW, false))
    override val showB30Overflow: Flow<Boolean> = _showB30Overflow.asStateFlow()

    private val _overflowCount = MutableStateFlow(prefs.getInt(KEY_OVERFLOW_COUNT, 9))
    override val overflowCount: Flow<Int> = _overflowCount.asStateFlow()

    override suspend fun setThemeMode(mode: Int) {
        prefs.edit { putInt(KEY_THEME_MODE, mode) }
        _themeMode.value = mode
    }



    override suspend fun setShowB30Overflow(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_B30_OVERFLOW, show) }
        _showB30Overflow.value = show
    }

    override suspend fun setOverflowCount(count: Int) {
        prefs.edit { putInt(KEY_OVERFLOW_COUNT, count) }
        _overflowCount.value = count
    }

    override suspend fun getPreloadDone(): Boolean {
        return preloadPrefs.getBoolean(KEY_PRELOAD_DONE, false)
    }

    override suspend fun setPreloadDone(done: Boolean) {
        preloadPrefs.edit(commit = true) { putBoolean(KEY_PRELOAD_DONE, done) }
    }

    private val _avatarUri = MutableStateFlow(prefs.getString(KEY_AVATAR_URI, null))
    override val avatarUri: Flow<String?> = _avatarUri.asStateFlow()

    override suspend fun setAvatarUri(uri: String?) {
        prefs.edit { putString(KEY_AVATAR_URI, uri) }
        _avatarUri.value = uri
    }

    private val _moneyString = MutableStateFlow(prefs.getString(KEY_MONEY_STRING, "") ?: "")
    override val moneyString: Flow<String> = _moneyString.asStateFlow()

    override suspend fun setMoneyString(money: String) {
        prefs.edit { putString(KEY_MONEY_STRING, money) }
        _moneyString.value = money
    }

    private val _includePreRelease = MutableStateFlow(prefs.getBoolean(KEY_INCLUDE_PRE_RELEASE, false))
    override val includePreRelease: Flow<Boolean> = _includePreRelease.asStateFlow()

    override suspend fun setIncludePreRelease(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INCLUDE_PRE_RELEASE, enabled) }
        _includePreRelease.value = enabled
    }

    private val _apiEnabled = MutableStateFlow(prefs.getBoolean(KEY_API_ENABLED, false))
    override val apiEnabled: Flow<Boolean> = _apiEnabled.asStateFlow()

    override suspend fun setApiEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_API_ENABLED, enabled) }
        _apiEnabled.value = enabled
    }

    private val _useApiData = MutableStateFlow(prefs.getBoolean(KEY_USE_API_DATA, false))
    override val useApiData: Flow<Boolean> = _useApiData.asStateFlow()

    override suspend fun setUseApiData(useApiData: Boolean) {
        prefs.edit { putBoolean(KEY_USE_API_DATA, useApiData) }
        _useApiData.value = useApiData
    }

    private val _apiId = MutableStateFlow(prefs.getString(KEY_API_ID, "") ?: "")
    override val apiId: Flow<String> = _apiId.asStateFlow()

    override suspend fun setApiId(apiId: String) {
        val normalized = apiId.trim()
        prefs.edit { putString(KEY_API_ID, normalized) }
        _apiId.value = normalized
    }

    private val _apiPlatform = MutableStateFlow(prefs.getString(KEY_API_PLATFORM, "") ?: "")
    override val apiPlatform: Flow<String> = _apiPlatform.asStateFlow()

    override suspend fun setApiPlatform(platform: String) {
        val normalized = platform.trim()
        prefs.edit { putString(KEY_API_PLATFORM, normalized) }
        _apiPlatform.value = normalized
    }

    private val _apiPlatformId = MutableStateFlow(prefs.getString(KEY_API_PLATFORM_ID, "") ?: "")
    override val apiPlatformId: Flow<String> = _apiPlatformId.asStateFlow()

    override suspend fun setApiPlatformId(platformId: String) {
        val normalized = platformId.trim()
        prefs.edit { putString(KEY_API_PLATFORM_ID, normalized) }
        _apiPlatformId.value = normalized
    }
}
