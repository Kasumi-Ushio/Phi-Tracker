package org.kasumi321.ushio.phitracker.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.api.BindRequest
import org.kasumi321.ushio.phitracker.data.api.GitHubRelease
import org.kasumi321.ushio.phitracker.data.api.PhiPluginApi
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
import kotlinx.serialization.json.Json

class PhigrosRepositoryImpl(
    private val apiClient: TapTapApiClient,
    private val phiPluginApi: PhiPluginApi,
    private val httpClient: HttpClient,
    private val saveParser: SaveParser,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val tokenManager: TokenManager,
    private val json: Json,
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
        val userInfo = apiClient.getUserInfo(sessionToken, server)
        val saveList = apiClient.getGameSaves(sessionToken, server, userInfo.objectId)
        val latestSave = saveList.results.firstOrNull { it.user?.objectId == userInfo.objectId }
            ?: error("没有找到当前用户的存档")
        val summary = saveParser.parseSummary(latestSave.summary)
        val saveData = apiClient.downloadSave(latestSave.gameFile.url)
        val save = saveParser.parseSave(saveData).copy(summary = summary)
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

    override fun clearTokenSync() {
        tokenManager.clearToken()
    }

    override suspend fun apiTest(): Result<JsonObject> =
        runCatching { phiPluginApi.test() }

    override suspend fun apiBind(platform: String, platformId: String, token: String): Result<JsonObject> =
        runCatching {
            phiPluginApi.bind(
                BindRequest(
                    platform = platform.trim(),
                    platformId = platformId.trim(),
                    token = token.trim()
                )
            )
        }

    override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
        runCatching { phiPluginApi.getBindInfo(platform.trim(), platformId.trim()) }

    override suspend fun apiGetSingleSave(
        platform: String,
        platformId: String,
        songId: String,
        difficulty: String
    ): Result<JsonObject> =
        runCatching { phiPluginApi.getSingleSave(platform.trim(), platformId.trim(), songId.trim(), difficulty.trim()) }

    override suspend fun apiGetSave(platform: String, platformId: String): Result<JsonObject> =
        runCatching { phiPluginApi.getSave(platform.trim(), platformId.trim()) }

    override suspend fun apiGetSaveInfo(platform: String, platformId: String): Result<JsonObject> =
        runCatching { phiPluginApi.getSaveInfo(platform.trim(), platformId.trim()) }

    override suspend fun apiGetRank(
        platform: String,
        platformId: String,
        songId: String,
        difficulty: String
    ): Result<JsonObject> =
        runCatching { phiPluginApi.getRank(platform.trim(), platformId.trim(), songId.trim(), difficulty.trim()) }

    override suspend fun apiGetAvgAcc(
        songId: String,
        difficulty: String,
        minRks: Float?,
        maxRks: Float?
    ): Result<JsonObject> =
        runCatching { phiPluginApi.getAvgAcc(songId.trim(), difficulty.trim(), minRks, maxRks) }

    override suspend fun apiGetAllAvgAcc(songIds: List<String>): Result<JsonObject> =
        runCatching { phiPluginApi.getAllAvgAcc(songIds.map { it.trim() }) }

    override suspend fun apiGetApFcTotal(songId: String): Result<JsonObject> =
        runCatching { phiPluginApi.getApFcTotal(songId.trim()) }

    override suspend fun apiGetFittedDifficulty(songId: String, difficulty: String): Result<JsonObject> =
        runCatching { phiPluginApi.getFittedDifficulty(songId.trim(), difficulty.trim()) }

    override suspend fun apiGetRksStats(): Result<JsonObject> =
        runCatching { phiPluginApi.getRksStats() }

    override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
        runCatching { phiPluginApi.getRksAbove(rks) }

    override suspend fun apiGetSaveHistory(
        platform: String,
        platformId: String,
        request: List<String>
    ): Result<JsonObject> =
        runCatching { phiPluginApi.getSaveHistory(platform.trim(), platformId.trim(), request.map { it.trim() }) }

    override suspend fun apiGetScoreHistory(
        platform: String,
        platformId: String,
        songId: String?,
        difficulty: String?
    ): Result<JsonObject> =
        runCatching {
            phiPluginApi.getScoreHistory(
                platform = platform.trim(),
                platformId = platformId.trim(),
                songId = songId?.trim(),
                difficulty = difficulty?.trim()
            )
        }

    override suspend fun apiGetRankByUser(platform: String, platformId: String): Result<JsonObject> =
        runCatching { phiPluginApi.getRankByUser(platform.trim(), platformId.trim()) }

    override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
        runCatching { phiPluginApi.getRankByPosition(position) }

    override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<GitHubRelease> =
        runCatching {
            val response = httpClient.get("https://api.github.com/repos/Kasumi-Ushio/Ushio-Prober-Phigros/releases") {
                headers.append("Accept", "application/vnd.github+json")
            }

            val statusCode = response.status
            val responseText = response.bodyAsText()

            if (statusCode == HttpStatusCode.Forbidden && responseText.contains("\"API rate limit\"")) {
                error("GitHub API 请求频率超限，请稍后再试")
            }
            if (!statusCode.value.toString().startsWith("2")) {
                error("GitHub 服务异常（${statusCode.value}），请稍后再试")
            }

            val releases = try {
                json.decodeFromString<List<GitHubRelease>>(responseText)
            } catch (e: SerializationException) {
                error("GitHub 返回数据格式异常，请稍后再试")
            }

            val candidates = if (includePreRelease) releases else releases.filter { !it.prerelease }
            candidates.firstOrNull() ?: error("未找到任何发布版本")
        }
}
