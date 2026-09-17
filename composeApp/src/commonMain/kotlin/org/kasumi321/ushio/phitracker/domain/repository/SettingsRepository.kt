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

    /**
     * B30 export card layout style: "classic" (default) or "score_focus".
     * Consumed as B30ExportCardStyle in ui.b30.
     */
    val b30CardStyle: Flow<String>
    suspend fun setB30CardStyle(style: String)

    /**
     * B30 export theme override: when [b30ThemeFollowGlobal] is true (default)
     * the export image follows the app theme; otherwise [b30ExportThemeMode]
     * decides. Mode values mirror themeMode (1 = light, 2 = dark, 3 = AMOLED);
     * a null value means the user never picked one and the current global
     * theme applies.
     */
    val b30ThemeFollowGlobal: Flow<Boolean>
    val b30ExportThemeMode: Flow<Int?>
    suspend fun setB30ThemeFollowGlobal(follow: Boolean)
    suspend fun setB30ExportThemeMode(mode: Int)

    val hazeBlurEnabled: Flow<Boolean>
    val hazeBlurStrength: Flow<Float>
    suspend fun setHazeBlurEnabled(enabled: Boolean)
    suspend fun setHazeBlurStrength(strength: Float)

    suspend fun setThemeMode(mode: Int)
    suspend fun setThemeColorSource(source: String)
    suspend fun setSeedColorArgb(argb: Int)
    suspend fun setThemeImageColor(uri: String?, seedColorArgb: Int)
    suspend fun clearThemeImageColor()
    suspend fun setPaletteStyleName(name: String)
    suspend fun setShowB30Overflow(show: Boolean)
    suspend fun setOverflowCount(count: Int)
    suspend fun getPreloadDone(): Boolean    suspend fun setPreloadDone(done: Boolean)

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

    val apiToken: Flow<String>
    suspend fun setApiToken(apiToken: String)

    val crashNotificationGuideShown: Flow<Boolean>
    suspend fun setCrashNotificationGuideShown(shown: Boolean)

    /**
     * JSON-serialized [org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo]
     * cache of the last fetched Phigros game update, so the profile page can
     * show the card offline. Null when nothing has been cached yet.
     */
    val gameUpdateInfoCache: Flow<String?>
    suspend fun setGameUpdateInfoCache(cache: String?)
}
