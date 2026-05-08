package org.kasumi321.ushio.phitracker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.database.UserDao
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toEntity
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toRecordEntities
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toSongRecordMap
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toUserProfile
import org.kasumi321.ushio.phitracker.data.mapper.currentTimeMillis
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.data.platform.TokenManager
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

class PhigrosRepositoryImpl(
    private val apiClient: TapTapApiClient,
    private val saveParser: SaveParser,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) : PhigrosRepository {
    override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> = runCatching {
        val userInfo = apiClient.getUserInfo(sessionToken, server)
        UserProfile(
            playerId = userInfo.objectId,
            nickname = userInfo.nickname,
            avatar = "",
            selfIntro = "",
            background = "",
            rks = 0f,
            challengeModeRank = 0,
            gameVersion = 0,
            updatedAt = ""
        )
    }

    override suspend fun syncSave(sessionToken: String, server: Server): Result<Save> = runCatching {
        val saveList = apiClient.getGameSaves(sessionToken, server)
        val latestSave = saveList.results.firstOrNull() ?: error("没有找到存档")
        val summary = saveParser.parseSummary(latestSave.summary)
        val saveData = apiClient.downloadSave(latestSave.gameFile.url)
        val save = saveParser.parseSave(saveData).copy(summary = summary)
        val userInfo = apiClient.getUserInfo(sessionToken, server)
        val userProfile = UserProfile(
            playerId = userInfo.objectId,
            nickname = userInfo.nickname,
            avatar = save.user.avatar,
            selfIntro = save.user.selfIntro,
            background = save.user.background,
            rks = summary.rks,
            challengeModeRank = summary.challengeModeRank,
            gameVersion = summary.gameVersion,
            updatedAt = latestSave.updatedAt
        )
        userDao.insertOrUpdate(userProfile.toEntity(server))
        val now = currentTimeMillis()
        val recordEntities = save.toRecordEntities(now)
        recordDao.deleteAll()
        recordDao.insertAll(recordEntities)
        save
    }

    override fun getCachedSave(): Flow<Save?> = combine(recordDao.getAllRecords(), userDao.getUser()) { records, user ->
        if (records.isEmpty()) return@combine null
        Save(
            gameRecord = records.toSongRecordMap(),
            gameProgress = GameProgress(
                isFirstRun = false,
                legacyChapterFinished = false,
                alreadyShowCollectionTip = false,
                alreadyShowAutoUnlockINTip = false,
                completed = "",
                songUpdateInfo = 0,
                challengeModeRank = user?.challengeModeRank ?: 0,
                money = emptyList(),
                unlockFlagOfSpasmodic = 0,
                unlockFlagOfIgallta = 0,
                unlockFlagOfRrharil = 0,
                flagOfSongRecordKey = 0,
                randomVersionUnlocked = null,
                chapter8UnlockBegin = null,
                chapter8UnlockSecondPhase = null,
                chapter8Passed = null,
                chapter8SongUnlocked = null
            ),
            user = UserSettings(
                showPlayerId = true,
                selfIntro = user?.selfIntro ?: "",
                avatar = user?.avatar ?: "",
                background = user?.background ?: ""
            ),
            summary = null
        )
    }

    override fun getUserProfile(): Flow<UserProfile?> = userDao.getUser().map { it?.toUserProfile() }

    override suspend fun saveSessionToken(token: String, server: Server) {
        tokenManager.saveToken(token, server)
    }

    override suspend fun getSessionToken(): Pair<String, Server>? = tokenManager.getToken()

    override suspend fun clearData() {
        tokenManager.clearToken()
        recordDao.deleteAll()
        userDao.deleteAll()
    }
}
