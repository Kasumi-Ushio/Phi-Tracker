package org.kasumi321.ushio.phitracker.data.mapper

import org.kasumi321.ushio.phitracker.data.database.RecordEntity
import org.kasumi321.ushio.phitracker.data.database.UserEntity
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.UserProfile

/**
 * Entity ↔ Domain 模型转换
 */
object EntityMapper {

    // ===== Record =====

    fun Save.toRecordEntities(updatedAt: Long): List<RecordEntity> {
        val entities = mutableListOf<RecordEntity>()
        for ((songId, songRecord) in this.gameRecord) {
            for ((difficulty, levelRecord) in songRecord.levels) {
                if (levelRecord != null) {
                    entities.add(
                        RecordEntity(
                            songId = songId,
                            difficulty = difficulty.name,
                            score = levelRecord.score,
                            accuracy = levelRecord.accuracy,
                            isFullCombo = levelRecord.isFullCombo,
                            updatedAt = updatedAt
                        )
                    )
                }
            }
        }
        return entities
    }

    fun List<RecordEntity>.toSongRecordMap(): Map<String, SongRecord> {
        return this.groupBy { it.songId }.mapValues { (songId, records) ->
            val levels = mutableMapOf<Difficulty, LevelRecord?>()
            for (diff in Difficulty.entries) {
                val record = records.find { it.difficulty == diff.name }
                levels[diff] = record?.let {
                    LevelRecord(
                        score = it.score,
                        accuracy = it.accuracy,
                        isFullCombo = it.isFullCombo
                    )
                }
            }
            SongRecord(songId = songId, levels = levels)
        }
    }

    // ===== User =====

    fun UserEntity.toUserProfile(): UserProfile {
        return UserProfile(
            playerId = playerId,
            nickname = nickname,
            avatar = avatar,
            selfIntro = selfIntro,
            background = background,
            rks = rks,
            challengeModeRank = challengeModeRank,
            gameVersion = gameVersion,
            updatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date(lastSyncAt))
        )
    }

    fun UserProfile.toEntity(server: Server): UserEntity {
        return UserEntity(
            playerId = playerId,
            nickname = nickname,
            avatar = avatar,
            selfIntro = selfIntro,
            background = background,
            rks = rks,
            challengeModeRank = challengeModeRank,
            gameVersion = gameVersion,
            server = server.name,
            lastSyncAt = System.currentTimeMillis()
        )
    }
}
