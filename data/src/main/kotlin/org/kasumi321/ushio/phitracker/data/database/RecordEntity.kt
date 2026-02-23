package org.kasumi321.ushio.phitracker.data.database

import androidx.room.Entity

/**
 * 成绩记录实体
 * 复合主键: songId + difficulty
 */
@Entity(tableName = "records", primaryKeys = ["songId", "difficulty"])
data class RecordEntity(
    val songId: String,
    val difficulty: String,
    val score: Int,
    val accuracy: Float,
    val isFullCombo: Boolean,
    val updatedAt: Long
)
