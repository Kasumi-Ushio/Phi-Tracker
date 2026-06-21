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

    private val themeColorSourceState = MutableStateFlow(storage.getString(KEY_THEME_COLOR_SOURCE) ?: "system")
    override val themeColorSource: Flow<String> = themeColorSourceState.asStateFlow()

    private val seedColorArgbState = MutableStateFlow(storage.getString(KEY_SEED_COLOR_ARGB)?.toIntOrNull() ?: -10011977)
    override val seedColorArgb: Flow<Int> = seedColorArgbState.asStateFlow()

    private val themeImageSeedColorArgbState = MutableStateFlow(storage.getString(KEY_THEME_IMAGE_SEED_COLOR_ARGB)?.toIntOrNull())
    override val themeImageSeedColorArgb: Flow<Int?> = themeImageSeedColorArgbState.asStateFlow()

    private val themeImageUriState = MutableStateFlow(storage.getString(KEY_THEME_IMAGE_URI))
    override val themeImageUri: Flow<String?> = themeImageUriState.asStateFlow()

    private val paletteStyleNameState = MutableStateFlow(storage.getString(KEY_PALETTE_STYLE_NAME) ?: "TonalSpot")
    override val paletteStyleName: Flow<String> = paletteStyleNameState.asStateFlow()

    private val showB30OverflowState = MutableStateFlow(storage.getString(KEY_SHOW_B30_OVERFLOW)?.toBooleanStrictOrNull() ?: false)
    override val showB30Overflow: Flow<Boolean> = showB30OverflowState.asStateFlow()

    private val overflowCountState = MutableStateFlow(
        storage.getString(KEY_OVERFLOW_COUNT)?.toIntOrNull()?.coerceIn(1, 30) ?: 9
    )
    override val overflowCount: Flow<Int> = overflowCountState.asStateFlow()

    override suspend fun setThemeMode(mode: Int) {
        storage.putString(KEY_THEME_MODE, mode.toString())
        themeModeState.value = mode
    }

    override suspend fun setThemeColorSource(source: String) {
        storage.putString(KEY_THEME_COLOR_SOURCE, source)
        themeColorSourceState.value = source
    }

    override suspend fun setSeedColorArgb(argb: Int) {
        storage.putString(KEY_SEED_COLOR_ARGB, argb.toString())
        seedColorArgbState.value = argb
    }

    override suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int) {
        if (uri == null) {
            storage.remove(KEY_THEME_IMAGE_URI)
        } else {
            storage.putString(KEY_THEME_IMAGE_URI, uri)
        }
        storage.putString(KEY_THEME_IMAGE_SEED_COLOR_ARGB, seedColorArgb.toString())
        themeImageUriState.value = uri
        themeImageSeedColorArgbState.value = seedColorArgb
    }

    override suspend fun clearThemeImageColor() {
        storage.remove(KEY_THEME_IMAGE_URI)
        storage.remove(KEY_THEME_IMAGE_SEED_COLOR_ARGB)
        themeImageUriState.value = null
        themeImageSeedColorArgbState.value = null
    }

    override suspend fun setPaletteStyleName(name: String) {
        storage.putString(KEY_PALETTE_STYLE_NAME, name)
        paletteStyleNameState.value = name
    }

    override suspend fun setShowB30Overflow(show: Boolean) {
        storage.putString(KEY_SHOW_B30_OVERFLOW, show.toString())
        showB30OverflowState.value = show
    }

    override suspend fun setOverflowCount(count: Int) {
        val normalized = count.coerceIn(1, 30)
        storage.putString(KEY_OVERFLOW_COUNT, normalized.toString())
        overflowCountState.value = normalized
    }

    override suspend fun getPreloadDone(): Boolean = preloadStorage.getString(KEY_PRELOAD_DONE)?.toBooleanStrictOrNull() ?: false

    override suspend fun setPreloadDone(done: Boolean) {
        preloadStorage.putString(KEY_PRELOAD_DONE, done.toString())
    }

    private val avatarUriState = MutableStateFlow(storage.getString(KEY_AVATAR_URI))
    override val avatarUri: Flow<String?> = avatarUriState.asStateFlow()

    override suspend fun setAvatarUri(uri: String?) {
        if (uri == null) {
            storage.remove(KEY_AVATAR_URI)
            avatarUriState.value = null
        } else {
            storage.putString(KEY_AVATAR_URI, uri)
            avatarUriState.value = uri
        }
    }

    private val moneyStringState = MutableStateFlow(storage.getString(KEY_MONEY_STRING) ?: "")
    override val moneyString: Flow<String> = moneyStringState.asStateFlow()

    override suspend fun setMoneyString(money: String) {
        storage.putString(KEY_MONEY_STRING, money)
        moneyStringState.value = money
    }

    private val includePreReleaseState = MutableStateFlow(storage.getString(KEY_INCLUDE_PRE_RELEASE)?.toBooleanStrictOrNull() ?: false)
    override val includePreRelease: Flow<Boolean> = includePreReleaseState.asStateFlow()

    override suspend fun setIncludePreRelease(enabled: Boolean) {
        storage.putString(KEY_INCLUDE_PRE_RELEASE, enabled.toString())
        includePreReleaseState.value = enabled
    }

    private val autoCheckUpdateState = MutableStateFlow(storage.getString(KEY_AUTO_CHECK_UPDATE)?.toBooleanStrictOrNull() ?: true)
    override val autoCheckUpdate: Flow<Boolean> = autoCheckUpdateState.asStateFlow()

    override suspend fun setAutoCheckUpdate(enabled: Boolean) {
        storage.putString(KEY_AUTO_CHECK_UPDATE, enabled.toString())
        autoCheckUpdateState.value = enabled
    }

    private val apiEnabledState = MutableStateFlow(storage.getString(KEY_API_ENABLED)?.toBooleanStrictOrNull() ?: false)
    override val apiEnabled: Flow<Boolean> = apiEnabledState.asStateFlow()

    override suspend fun setApiEnabled(enabled: Boolean) {
        storage.putString(KEY_API_ENABLED, enabled.toString())
        apiEnabledState.value = enabled
    }

    private val useApiDataState = MutableStateFlow(storage.getString(KEY_USE_API_DATA)?.toBooleanStrictOrNull() ?: false)
    override val useApiData: Flow<Boolean> = useApiDataState.asStateFlow()

    override suspend fun setUseApiData(useApiData: Boolean) {
        storage.putString(KEY_USE_API_DATA, useApiData.toString())
        useApiDataState.value = useApiData
    }

    private val apiIdState = MutableStateFlow(storage.getString(KEY_API_ID)?.trim() ?: "")
    override val apiId: Flow<String> = apiIdState.asStateFlow()

    override suspend fun setApiId(apiId: String) {
        val trimmed = apiId.trim()
        storage.putString(KEY_API_ID, trimmed)
        apiIdState.value = trimmed
    }

    private val apiPlatformState = MutableStateFlow(storage.getString(KEY_API_PLATFORM)?.trim() ?: "")
    override val apiPlatform: Flow<String> = apiPlatformState.asStateFlow()

    override suspend fun setApiPlatform(platform: String) {
        val trimmed = platform.trim()
        storage.putString(KEY_API_PLATFORM, trimmed)
        apiPlatformState.value = trimmed
    }

    private val apiPlatformIdState = MutableStateFlow(storage.getString(KEY_API_PLATFORM_ID)?.trim() ?: "")
    override val apiPlatformId: Flow<String> = apiPlatformIdState.asStateFlow()

    override suspend fun setApiPlatformId(platformId: String) {
        val trimmed = platformId.trim()
        storage.putString(KEY_API_PLATFORM_ID, trimmed)
        apiPlatformIdState.value = trimmed
    }

    private val crashNotificationGuideShownState = MutableStateFlow(storage.getString(KEY_CRASH_NOTIFICATION_GUIDE_SHOWN)?.toBooleanStrictOrNull() ?: false)
    override val crashNotificationGuideShown: Flow<Boolean> = crashNotificationGuideShownState.asStateFlow()

    override suspend fun setCrashNotificationGuideShown(shown: Boolean) {
        storage.putString(KEY_CRASH_NOTIFICATION_GUIDE_SHOWN, shown.toString())
        crashNotificationGuideShownState.value = shown
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_THEME_COLOR_SOURCE = "theme_color_source"
        const val KEY_SEED_COLOR_ARGB = "seed_color_argb"
        const val KEY_THEME_IMAGE_SEED_COLOR_ARGB = "theme_image_seed_color_argb"
        const val KEY_THEME_IMAGE_URI = "theme_image_uri"
        const val KEY_PALETTE_STYLE_NAME = "palette_style_name"
        const val KEY_SHOW_B30_OVERFLOW = "show_b30_overflow"
        const val KEY_OVERFLOW_COUNT = "overflow_count"
        const val KEY_PRELOAD_DONE = "preload_done"
        const val KEY_AVATAR_URI = "avatar_uri"
        const val KEY_MONEY_STRING = "money_string"
        const val KEY_INCLUDE_PRE_RELEASE = "include_pre_release"
        const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
        const val KEY_API_ENABLED = "api_enabled"
        const val KEY_USE_API_DATA = "use_api_data"
        const val KEY_API_ID = "api_id"
        const val KEY_API_PLATFORM = "api_platform"
        const val KEY_API_PLATFORM_ID = "api_platform_id"
        const val KEY_CRASH_NOTIFICATION_GUIDE_SHOWN = "crash_notification_guide_shown"
    }
}
