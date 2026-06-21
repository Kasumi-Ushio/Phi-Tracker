package org.kasumi321.ushio.phitracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<Int>
    val themeColorSource: Flow<String>
    val seedColorArgb: Flow<Int>
    val themeImageSeedColorArgb: Flow<Int?>
    val themeImageUri: Flow<String?>
    val paletteStyleName: Flow<String>
    val showB30Overflow: Flow<Boolean>
    val overflowCount: Flow<Int>

    suspend fun setThemeMode(mode: Int)
    suspend fun setThemeColorSource(source: String)
    suspend fun setSeedColorArgb(argb: Int)
    suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int)
    suspend fun clearThemeImageColor()
    suspend fun setPaletteStyleName(name: String)
    suspend fun setShowB30Overflow(show: Boolean)
    suspend fun setOverflowCount(count: Int)
    suspend fun getPreloadDone(): Boolean
    suspend fun setPreloadDone(done: Boolean)

    val avatarUri: Flow<String?>
    suspend fun setAvatarUri(uri: String?)

    val moneyString: Flow<String>
    suspend fun setMoneyString(money: String)

    val includePreRelease: Flow<Boolean>
    val autoCheckUpdate: Flow<Boolean>
    suspend fun setIncludePreRelease(enabled: Boolean)
    suspend fun setAutoCheckUpdate(enabled: Boolean)

    val apiEnabled: Flow<Boolean>
    suspend fun setApiEnabled(enabled: Boolean)

    val useApiData: Flow<Boolean>
    suspend fun setUseApiData(useApiData: Boolean)

    val apiId: Flow<String>
    suspend fun setApiId(apiId: String)

    val apiPlatform: Flow<String>
    suspend fun setApiPlatform(platform: String)

    val apiPlatformId: Flow<String>
    suspend fun setApiPlatformId(platformId: String)

    val crashNotificationGuideShown: Flow<Boolean>
    suspend fun setCrashNotificationGuideShown(shown: Boolean)
}
