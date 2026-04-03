package org.kasumi321.ushio.phitracker.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<Int>
    val showB30Overflow: Flow<Boolean>
    val overflowCount: Flow<Int>

    suspend fun setThemeMode(mode: Int)
    suspend fun setShowB30Overflow(show: Boolean)
    suspend fun setOverflowCount(count: Int)
    
    // 曲绘下载状态
    suspend fun getPreloadDone(): Boolean
    suspend fun setPreloadDone(done: Boolean)

    // 头像
    val avatarUri: Flow<String?>
    suspend fun setAvatarUri(uri: String?)

    // Data (货币)
    val moneyString: Flow<String>
    suspend fun setMoneyString(money: String)

    // 更新频道
    val includePreRelease: Flow<Boolean>
    suspend fun setIncludePreRelease(enabled: Boolean)

    // 统一查分 API
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
}
