package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 曲目同步历史实体
 * 记录每次同步中发生变化的成绩
 */
@Entity(tableName = "song_sync_history")
data class SongSyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snapshotId: Long,
    val songId: String,
    val difficulty: String,
    val score: Int,
    val accuracy: Float,
    val isFullCombo: Boolean,
    val timestamp: Long
)
