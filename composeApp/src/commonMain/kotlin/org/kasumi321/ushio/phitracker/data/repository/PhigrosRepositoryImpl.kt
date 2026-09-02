package org.kasumi321.ushio.phitracker.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kasumi321.ushio.phitracker.data.api.GitHubReleaseDto
import org.kasumi321.ushio.phitracker.data.api.toDomain
import org.kasumi321.ushio.phitracker.data.api.PhiPluginApi
import org.kasumi321.ushio.phitracker.data.api.TapTapApiClient
import org.kasumi321.ushio.phitracker.data.database.RecordDao
import org.kasumi321.ushio.phitracker.data.database.AppDatabase
import org.kasumi321.ushio.phitracker.data.database.SongSyncHistoryDao
import org.kasumi321.ushio.phitracker.data.database.SyncSnapshotDao
import org.kasumi321.ushio.phitracker.data.database.SyncWriter
import org.kasumi321.ushio.phitracker.data.database.UserDao
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toSongRecordMap
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toDomain
import org.kasumi321.ushio.phitracker.data.mapper.EntityMapper.toUserProfile
import org.kasumi321.ushio.phitracker.data.parser.SaveParser
import org.kasumi321.ushio.phitracker.data.platform.TokenManager
import org.kasumi321.ushio.phitracker.data.song.SongDataProvider
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey
import org.kasumi321.ushio.phitracker.domain.model.SongApiDetail
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

class PhigrosRepositoryImpl(
    private val apiClient: TapTapApiClient,
    private val phiPluginApi: PhiPluginApi,
    private val httpClient: HttpClient,
    private val saveParser: SaveParser,
    database: AppDatabase,
    private val recordDao: RecordDao,
    private val userDao: UserDao,
    private val syncSnapshotDao: SyncSnapshotDao,
    private val songSyncHistoryDao: SongSyncHistoryDao,
    private val tokenManager: TokenManager,
    private val json: Json,
    private val songDataProvider: SongDataProvider,
) : PhigrosRepository {
    private val syncWriter = SyncWriter(database, recordDao, userDao, syncSnapshotDao, songSyncHistoryDao)
    private val syncSnapshotProjector = SyncSnapshotProjector()
    private val apiDetailMutex = Mutex()
    private val apiDetailCache = mutableMapOf<ApiDetailCacheKey, SongApiDetail>()
    private var apiDetailEpoch = 0L
    private var apiDetailIdentity: Triple<String, String, String>? = null

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

    override suspend fun syncSave(
        sessionToken: String,
        server: Server,
        mode: SyncMode
    ): Result<SyncSaveResult> = runCatching {
        val previousPlayerId = userDao.getUser().first()?.playerId
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
        val commitData = syncSnapshotProjector.project(
            save = save,
            profile = userProfile,
            server = server,
            difficulties = songDataProvider.getDifficultyMap(),
            songNames = songDataProvider.getSongNameMap()
        )
        syncWriter.commit(commitData, mode).also {
            if (previousPlayerId != userProfile.playerId) {
                invalidateApiDetailCache()
            }
        }
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
        invalidateApiDetailCache()
        tokenManager.saveToken(token, server)
    }

    override suspend fun getSessionToken(): Pair<String, Server>? = tokenManager.getToken()

    override suspend fun clearData() {
        invalidateApiDetailCache()
        tokenManager.clearToken()
        recordDao.deleteAll()
        userDao.deleteAll()
    }

    override suspend fun clearTokenSync() {
        invalidateApiDetailCache()
        tokenManager.clearToken()
    }

    override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> =
        Difficulty.entries.associateWith { difficulty ->
            recordDao.getClearCountByDifficulty(difficulty.name)
        }

    override suspend fun getTotalFullComboCount(): Int = recordDao.getTotalFcCount()

    override suspend fun getTotalPhiCount(): Int = recordDao.getTotalPhiCount()

    override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> =
        syncSnapshotDao.getAll().map { snapshots -> snapshots.map { it.toDomain() } }

    override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> =
        syncSnapshotDao.getAllOnce().map { it.toDomain() }

    override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> =
        songSyncHistoryDao.getBySongId(songId).map { entries -> entries.map { it.toDomain() } }

    override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> =
        songSyncHistoryDao.getBySnapshotId(snapshotId).map { it.toDomain() }

    override suspend fun apiTest(): Result<JsonObject> =
        runCatching { phiPluginApi.test() }

    override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
        runCatching { phiPluginApi.getBindInfo(platform.trim(), platformId.trim()) }

    override suspend fun getSongApiDetail(key: ApiDetailCacheKey): Result<SongApiDetail> {
        val normalizedKey = key.copy(
            platform = key.platform.trim(),
            platformId = key.platformId.trim(),
            apiUserId = key.apiUserId.trim(),
            songId = key.songId.trim()
        )
        val identity = Triple(normalizedKey.platform, normalizedKey.platformId, normalizedKey.apiUserId)
        val epochAtStart: Long
        apiDetailMutex.withLock {
            if (apiDetailIdentity != null && apiDetailIdentity != identity) {
                advanceApiDetailEpochLocked()
            }
            apiDetailIdentity = identity
            apiDetailCache[normalizedKey]?.let { return Result.success(it) }
            epochAtStart = apiDetailEpoch
        }

        val result = runCatching {
            val difficulty = normalizedKey.difficulty.name
            val rank = phiPluginApi.getRank(
                normalizedKey.platform,
                normalizedKey.platformId,
                normalizedKey.apiUserId,
                normalizedKey.songId,
                difficulty
            )
            val average = phiPluginApi.getAvgAcc(
                normalizedKey.songId,
                difficulty,
                normalizedKey.minRks,
                normalizedKey.maxRks
            )
            val history = phiPluginApi.getScoreHistory(
                normalizedKey.platform,
                normalizedKey.platformId,
                normalizedKey.apiUserId,
                normalizedKey.songId,
                difficulty
            )
            mapSongApiDetail(rank, average, history, normalizedKey)
        }

        result.getOrNull()?.let { detail ->
            apiDetailMutex.withLock {
                if (apiDetailEpoch == epochAtStart && apiDetailIdentity == identity) {
                    apiDetailCache[normalizedKey] = detail
                }
            }
        }
        return result
    }

    override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
        runCatching { phiPluginApi.getRksAbove(rks) }

    override suspend fun apiGetSaveHistory(
        platform: String,
        platformId: String,
        apiUserId: String,
        request: List<String>
    ): Result<JsonObject> =
        runCatching {
            phiPluginApi.getSaveHistory(
                platform.trim(),
                platformId.trim(),
                apiUserId.trim(),
                request.map { it.trim() }
            )
        }

    override suspend fun apiGetRankByUser(
        platform: String,
        platformId: String,
        apiUserId: String
    ): Result<JsonObject> =
        runCatching { phiPluginApi.getRankByUser(platform.trim(), platformId.trim(), apiUserId.trim()) }

    override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
        runCatching { phiPluginApi.getRankByPosition(position) }

    override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo> =
        runCatching {
            val response = httpClient.get("https://api.github.com/repos/Kasumi-Ushio/Ushio-Prober-Phigros/releases") {
                headers.append("Accept", "application/vnd.github+json")
            }

            parseGitHubReleaseResponse(
                statusCode = response.status,
                responseText = response.bodyAsText(),
                includePreRelease = includePreRelease,
                json = json,
            ).getOrThrow()
        }

    private suspend fun invalidateApiDetailCache() {
        apiDetailMutex.withLock { advanceApiDetailEpochLocked() }
    }

    private fun advanceApiDetailEpochLocked() {
        apiDetailEpoch++
        apiDetailCache.clear()
        apiDetailIdentity = null
    }

    private fun mapSongApiDetail(
        rank: JsonObject,
        average: JsonObject,
        history: JsonObject,
        key: ApiDetailCacheKey
    ): SongApiDetail {
        val rankData = rank["data"].asObject()
        val averageData = average["data"].asObject()
        return SongApiDetail(
            userRank = rankData?.get("userRank").asInt(),
            totalUsers = rankData?.get("totDataNum").asInt(),
            avgAcc = averageData?.get("accAvg").asFloat(),
            avgAccCount = averageData?.get("count").asInt(),
            history = parseSongHistory(history["data"], key.songId, key.difficulty)
        )
    }

    private fun parseSongHistory(
        element: JsonElement?,
        songId: String,
        difficulty: Difficulty
    ): List<SongSyncHistoryEntry> {
        val records = element.asArray()
            ?: element.asObject()?.get(difficulty.name).asArray()
            ?: return emptyList()
        return records.mapNotNull { row ->
            val values = row.asArray()?.takeIf { it.size >= 4 } ?: return@mapNotNull null
            val accuracy = values[0].asFloat() ?: return@mapNotNull null
            val score = values[1].asInt() ?: return@mapNotNull null
            val date = values[2].asString() ?: return@mapNotNull null
            SongSyncHistoryEntry(
                id = 0L,
                snapshotId = 0L,
                songId = songId,
                difficulty = difficulty.name,
                score = score,
                accuracy = accuracy,
                isFullCombo = values[3].asBoolean() ?: false,
                timestamp = runCatching { Instant.parse(date).toEpochMilliseconds() }
                    .getOrElse { Clock.System.now().toEpochMilliseconds() }
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun JsonElement?.asObject(): JsonObject? = runCatching { this?.jsonObject }.getOrNull()
    private fun JsonElement?.asArray(): JsonArray? = runCatching { this?.jsonArray }.getOrNull()
    private fun JsonElement?.asString(): String? = this?.jsonPrimitive?.contentOrNull
    private fun JsonElement?.asInt(): Int? = asString()?.toIntOrNull()
    private fun JsonElement?.asFloat(): Float? = asString()?.toFloatOrNull()
    private fun JsonElement?.asBoolean(): Boolean? = asString()?.toBooleanStrictOrNull()
}
