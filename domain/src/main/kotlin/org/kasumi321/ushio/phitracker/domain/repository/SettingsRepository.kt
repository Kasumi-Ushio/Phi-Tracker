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
}
