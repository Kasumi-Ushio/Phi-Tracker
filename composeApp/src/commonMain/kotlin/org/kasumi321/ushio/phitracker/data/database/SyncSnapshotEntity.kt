package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 同步快照实体
 * 每次存档同步成功后记录一条快照
 */
@Entity(tableName = "sync_snapshots")
data class SyncSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val rks: Float,
    val nickname: String,
    val dataCount: Int,
    val lastSyncedSongId: String?,
    val lastSyncedDifficulty: String?,
    val lastSyncedScore: Int?,
    val lastSyncedAccuracy: Float?
)
