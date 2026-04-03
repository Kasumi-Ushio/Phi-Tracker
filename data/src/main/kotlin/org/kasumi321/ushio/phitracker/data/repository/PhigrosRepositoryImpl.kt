package org.kasumi321.ushio.phitracker.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.api.BindRequest
import org.kasumi321.ushio.phitracker.data.api.PhiPluginApi
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.database.UserDao
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toEntity
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toRecordEntities
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toSongRecordMap
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toUserProfile
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class PhigrosRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: TapTapApiClient,
    private val phiPluginApi: PhiPluginApi,
    private val saveParser: SaveParser,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val tokenManager: TokenManager
) : PhigrosRepository {

    override suspend fun validateToken(
        sessionToken: String,
        server: Server
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
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
    }

    override suspend fun syncSave(
        sessionToken: String,
        server: Server
    ): Result<Save> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. 获取存档列表
            val saveList = apiClient.getGameSaves(sessionToken, server)
            val latestSave = saveList.results.firstOrNull()
                ?: throw IllegalStateException("没有找到存档")
            Timber.d("Found %d saves, using latest", saveList.results.size)

            // 2. 解析 Summary
            val summary = saveParser.parseSummary(latestSave.summary)

            // 3. 下载存档 ZIP
            val saveData = apiClient.downloadSave(latestSave.gameFile.url)

            // 4. 解密和解析存档
            val tempDir = File(context.cacheDir, "save_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            val save = saveParser.parseSave(saveData, tempDir).copy(summary = summary)

            // 5. 将用户信息获取并保存
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

            // 6. 保存成绩到数据库
            val now = System.currentTimeMillis()
            val recordEntities = save.toRecordEntities(now)
            recordDao.deleteAll()
            recordDao.insertAll(recordEntities)
            Timber.i("Sync complete: %d records saved, rks=%.4f, nickname=%s",
                recordEntities.size, summary.rks, userInfo.nickname)

            save
        }
    }

    override fun getCachedSave(): Flow<Save?> {
        return combine(
            recordDao.getAllRecords(),
            userDao.getUser()
        ) { records, user ->
            if (records.isEmpty()) return@combine null

            val songRecordMap = records.toSongRecordMap()

            Save(
                gameRecord = songRecordMap,
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
    }

    override fun getUserProfile(): Flow<UserProfile?> {
        return userDao.getUser().map { entity ->
            entity?.toUserProfile()
        }
    }

    override suspend fun saveSessionToken(token: String, server: Server) {
        tokenManager.saveToken(token, server)
    }

    override suspend fun getSessionToken(): Pair<String, Server>? {
        return tokenManager.getToken()
    }

    override suspend fun clearData() {
        tokenManager.clearToken()
        recordDao.deleteAll()
        userDao.deleteAll()
    }

    override fun clearTokenSync() {
        tokenManager.clearToken()  // EncryptedSharedPreferences 使用 commit() 同步记录
    }

    override suspend fun apiTest(): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.test() }
    }

    override suspend fun apiBind(platform: String, platformId: String, token: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            phiPluginApi.bind(
                BindRequest(
                    platform = platform.trim(),
                    platformId = platformId.trim(),
                    token = token.trim()
                )
            )
        }
    }

    override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getBindInfo(platform.trim(), platformId.trim()) }
    }

    override suspend fun apiGetSingleSave(
        platform: String,
        platformId: String,
        songId: String,
        difficulty: String
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getSingleSave(platform.trim(), platformId.trim(), songId.trim(), difficulty.trim()) }
    }

    override suspend fun apiGetSave(platform: String, platformId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getSave(platform.trim(), platformId.trim()) }
    }

    override suspend fun apiGetSaveInfo(platform: String, platformId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getSaveInfo(platform.trim(), platformId.trim()) }
    }

    override suspend fun apiGetRank(
        platform: String,
        platformId: String,
        songId: String,
        difficulty: String
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getRank(platform.trim(), platformId.trim(), songId.trim(), difficulty.trim()) }
    }

    override suspend fun apiGetAvgAcc(
        songId: String,
        difficulty: String,
        minRks: Float?,
        maxRks: Float?
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getAvgAcc(songId.trim(), difficulty.trim(), minRks, maxRks) }
    }

    override suspend fun apiGetAllAvgAcc(songIds: List<String>): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getAllAvgAcc(songIds.map { it.trim() }) }
    }

    override suspend fun apiGetApFcTotal(songId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getApFcTotal(songId.trim()) }
    }

    override suspend fun apiGetFittedDifficulty(songId: String, difficulty: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getFittedDifficulty(songId.trim(), difficulty.trim()) }
    }

    override suspend fun apiGetRksStats(): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getRksStats() }
    }

    override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getRksAbove(rks) }
    }

    override suspend fun apiGetSaveHistory(
        platform: String,
        platformId: String,
        request: List<String>
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getSaveHistory(platform.trim(), platformId.trim(), request.map { it.trim() }) }
    }

    override suspend fun apiGetScoreHistory(
        platform: String,
        platformId: String,
        songId: String?,
        difficulty: String?
    ): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            phiPluginApi.getScoreHistory(
                platform = platform.trim(),
                platformId = platformId.trim(),
                songId = songId?.trim(),
                difficulty = difficulty?.trim()
            )
        }
    }

    override suspend fun apiGetRankByUser(platform: String, platformId: String): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getRankByUser(platform.trim(), platformId.trim()) }
    }

    override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> = withContext(Dispatchers.IO) {
        runCatching { phiPluginApi.getRankByPosition(position) }
    }
}
