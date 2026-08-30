package org.kasumi321.ushio.phitracker.domain.model

data class ApiDetailCacheKey(
    val platform: String,
    val platformId: String,
    val songId: String,
    val difficulty: Difficulty,
    val minRks: Float,
    val maxRks: Float
)

data class SongApiDetail(
    val userRank: Int?,
    val totalUsers: Int?,
    val avgAcc: Float?,
    val avgAccCount: Int?,
    val history: List<SongSyncHistoryEntry>
)
