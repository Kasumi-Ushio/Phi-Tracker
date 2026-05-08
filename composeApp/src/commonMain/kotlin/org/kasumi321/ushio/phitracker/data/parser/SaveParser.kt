package org.kasumi321.ushio.phitracker.data.parser

import org.kasumi321.ushio.phitracker.data.platform.Base64Codec
import org.kasumi321.ushio.phitracker.data.platform.ZipArchiveReader
import org.kasumi321.ushio.phitracker.data.platform.createZipArchiveReader
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.Summary
import org.kasumi321.ushio.phitracker.domain.model.UserSettings

class SaveParser(
    private val aesDecryptor: AesDecryptor,
    private val zipArchiveReader: ZipArchiveReader = createZipArchiveReader()
) {
    fun parseSave(saveData: ByteArray): Save {
        val entries = zipArchiveReader.readEntries(saveData)
        val gameRecord = parseGameRecord(readAndDecrypt(requiredEntry(entries, "gameRecord")))
        val gameProgress = parseGameProgress(readAndDecrypt(requiredEntry(entries, "gameProgress")))
        val user = parseUser(readAndDecrypt(requiredEntry(entries, "user")))
        return Save(gameRecord = gameRecord, gameProgress = gameProgress, user = user, summary = null)
    }

    fun parseSummary(summaryBase64: String): Summary {
        val reader = BinaryReader(Base64Codec.decode(summaryBase64))
        return Summary(
            saveVersion = reader.readByte(),
            challengeModeRank = reader.readShort(),
            rks = reader.readFloat(),
            gameVersion = reader.readVarShort(),
            avatar = reader.readString(),
            progress = List(12) { reader.readShort() }
        )
    }

    private fun requiredEntry(entries: Map<String, ByteArray>, name: String): ByteArray =
        entries[name] ?: error("Save archive missing entry: $name")

    private fun readAndDecrypt(data: ByteArray): Pair<Int, ByteArray> = aesDecryptor.decrypt(data)

    private fun parseGameRecord(versionAndData: Pair<Int, ByteArray>): Map<String, SongRecord> {
        val reader = BinaryReader(versionAndData.second)
        val songCount = reader.readVarShort()
        val records = mutableMapOf<String, SongRecord>()
        repeat(songCount) {
            val songId = reader.readString()
            val dataLength = reader.readByte()
            val startPos = reader.position
            val levelMask = reader.readByte()
            val fcMask = reader.readByte()
            val levels = mutableMapOf<Difficulty, LevelRecord?>()
            for (level in 0 until 4) {
                val difficulty = Difficulty.fromIndex(level)
                levels[difficulty] = if (levelMask shr level and 1 == 1) {
                    LevelRecord(
                        score = reader.readInt(),
                        accuracy = reader.readFloat(),
                        isFullCombo = fcMask shr level and 1 == 1
                    )
                } else {
                    null
                }
            }
            val consumed = reader.position - startPos
            if (consumed < dataLength) reader.skip(dataLength - consumed)
            records[songId] = SongRecord(songId = songId, levels = levels)
        }
        return records
    }

    private fun parseGameProgress(versionAndData: Pair<Int, ByteArray>): GameProgress {
        val version = versionAndData.first
        val reader = BinaryReader(versionAndData.second)
        val boolByte1 = reader.readByte()
        val isFirstRun = boolByte1 and 1 != 0
        val legacyChapterFinished = boolByte1 shr 1 and 1 != 0
        val alreadyShowCollectionTip = boolByte1 shr 2 and 1 != 0
        val alreadyShowAutoUnlockINTip = boolByte1 shr 3 and 1 != 0
        val completed = reader.readString()
        val songUpdateInfo = reader.readByte()
        val challengeModeRank = reader.readShort()
        val money = List(5) { reader.readVarShort() }
        val unlockFlagOfSpasmodic = reader.readByte()
        val unlockFlagOfIgallta = reader.readByte()
        val unlockFlagOfRrharil = reader.readByte()
        val flagOfSongRecordKey = reader.readByte()
        val randomVersionUnlocked = if (version >= 2) reader.readByte() else null
        var chapter8UnlockBegin: Boolean? = null
        var chapter8UnlockSecondPhase: Boolean? = null
        var chapter8Passed: Boolean? = null
        var chapter8SongUnlocked: Int? = null
        if (version >= 3) {
            val boolByte2 = reader.readByte()
            chapter8UnlockBegin = boolByte2 and 1 != 0
            chapter8UnlockSecondPhase = boolByte2 shr 1 and 1 != 0
            chapter8Passed = boolByte2 shr 2 and 1 != 0
            chapter8SongUnlocked = reader.readByte()
        }
        return GameProgress(
            isFirstRun = isFirstRun,
            legacyChapterFinished = legacyChapterFinished,
            alreadyShowCollectionTip = alreadyShowCollectionTip,
            alreadyShowAutoUnlockINTip = alreadyShowAutoUnlockINTip,
            completed = completed,
            songUpdateInfo = songUpdateInfo,
            challengeModeRank = challengeModeRank,
            money = money,
            unlockFlagOfSpasmodic = unlockFlagOfSpasmodic,
            unlockFlagOfIgallta = unlockFlagOfIgallta,
            unlockFlagOfRrharil = unlockFlagOfRrharil,
            flagOfSongRecordKey = flagOfSongRecordKey,
            randomVersionUnlocked = randomVersionUnlocked,
            chapter8UnlockBegin = chapter8UnlockBegin,
            chapter8UnlockSecondPhase = chapter8UnlockSecondPhase,
            chapter8Passed = chapter8Passed,
            chapter8SongUnlocked = chapter8SongUnlocked
        )
    }

    private fun parseUser(versionAndData: Pair<Int, ByteArray>): UserSettings {
        val reader = BinaryReader(versionAndData.second)
        val boolByte = reader.readByte()
        return UserSettings(
            showPlayerId = boolByte and 1 != 0,
            selfIntro = reader.readString(),
            avatar = reader.readString(),
            background = reader.readString()
        )
    }
}
