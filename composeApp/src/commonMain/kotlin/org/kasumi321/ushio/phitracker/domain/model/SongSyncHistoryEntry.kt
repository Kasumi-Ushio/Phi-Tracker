package org.kasumi321.ushio.phitracker.domain.model

data class SongSyncHistoryEntry(
    val id: Long,
    val snapshotId: Long,
    val songId: String,
    val difficulty: String,
    val score: Int,
    val accuracy: Float,
    val isFullCombo: Boolean,
    val timestamp: Long
)
