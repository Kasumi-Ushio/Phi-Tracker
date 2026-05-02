package org.kasumi321.ushio.phitracker.data.parser

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.kasumi321.ushio.phitracker.data.api.CryptoConstants
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.Summary
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.platform.AesCipher
import org.kasumi321.ushio.phitracker.platform.FileStorage
import org.kasumi321.ushio.phitracker.platform.PathProvider
import org.kasumi321.ushio.phitracker.platform.ZipExtractor

class SaveParser(
    private val aesDecryptor: AesDecryptor,
    private val zipExtractor: ZipExtractor,
    private val pathProvider: PathProvider,
    private val fileStorage: FileStorage
) {

    fun parseSave(saveData: ByteArray): Save {
        val tempDir = pathProvider.cacheDir() + "/save_extract"
        fileStorage.deleteRecursively(tempDir)

        zipExtractor.extract(saveData, tempDir)

        val gameRecord = parseGameRecord(
            aesDecryptor.decrypt(fileStorage.readFile("$tempDir/gameRecord")!!)
        )

        val gameProgress = parseGameProgress(
            aesDecryptor.decrypt(fileStorage.readFile("$tempDir/gameProgress")!!)
        )

        val user = parseUser(
            aesDecryptor.decrypt(fileStorage.readFile("$tempDir/user")!!)
        )

        fileStorage.deleteRecursively(tempDir)

        return Save(
            gameRecord = gameRecord,
            gameProgress = gameProgress,
            user = user,
            summary = null
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun parseSummary(summaryBase64: String): Summary {
        val data = Base64.decode(summaryBase64)
        val reader = BinaryReader(data)
        return Summary(
            saveVersion = reader.readByte(),
            challengeModeRank = reader.readShort(),
            rks = reader.readFloat(),
            gameVersion = reader.readVarShort(),
            avatar = reader.readString(),
            progress = List(12) { reader.readShort() }
        )
    }

    private fun parseGameRecord(versionAndData: Pair<Int, ByteArray>): Map<String, SongRecord> {
        val reader = BinaryReader(versionAndData.second)
        val songCount = reader.readVarShort()
        val records = mutableMapOf<String, SongRecord>()

        for (i in 0 until songCount) {
            val songId = reader.readString()
            val dataLength = reader.readByte()
            val startPos = reader.position

            val levelMask = reader.readByte()
            val fcMask = reader.readByte()

            val levels = mutableMapOf<Difficulty, LevelRecord?>()
            for (level in 0 until 4) {
                if (levelMask shr level and 1 == 1) {
                    val score = reader.readInt()
                    val acc = reader.readFloat()
                    val fc = fcMask shr level and 1 == 1
                    levels[Difficulty.fromIndex(level)] = LevelRecord(
                        score = score,
                        accuracy = acc,
                        isFullCombo = fc
                    )
                } else {
                    levels[Difficulty.fromIndex(level)] = null
                }
            }

            val consumed = reader.position - startPos
            if (consumed < dataLength) {
                reader.skip(dataLength - consumed)
            }

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

        var randomVersionUnlocked: Int? = null
        if (version >= 2) {
            randomVersionUnlocked = reader.readByte()
        }

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
        val showPlayerId = boolByte and 1 != 0

        val selfIntro = reader.readString()
        val avatar = reader.readString()
        val background = reader.readString()

        return UserSettings(
            showPlayerId = showPlayerId,
            selfIntro = selfIntro,
            avatar = avatar,
            background = background
        )
    }
}
