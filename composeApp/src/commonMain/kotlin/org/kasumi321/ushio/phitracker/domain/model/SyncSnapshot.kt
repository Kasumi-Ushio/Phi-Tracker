package org.kasumi321.ushio.phitracker.domain.model

data class SyncSnapshot(
    val id: Long,
    val timestamp: Long,
    val rks: Float,
    val nickname: String,
    val dataCount: Int,
    val lastSyncedSongId: String?,
    val lastSyncedDifficulty: String?,
    val lastSyncedScore: Int?,
    val lastSyncedAccuracy: Float?
)
