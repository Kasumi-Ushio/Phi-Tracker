package org.kasumi321.ushio.phitracker.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.api.GitHubRelease
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile

interface PhigrosRepository {
    suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile>
    suspend fun syncSave(sessionToken: String, server: Server, mode: SyncMode): Result<SyncSaveResult>
    fun getCachedSave(): Flow<Save?>
    fun getUserProfile(): Flow<UserProfile?>
    suspend fun saveSessionToken(token: String, server: Server)
    suspend fun getSessionToken(): Pair<String, Server>?
    suspend fun clearData()
    fun clearTokenSync()

    suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int>
    suspend fun getTotalFullComboCount(): Int
    suspend fun getTotalPhiCount(): Int
    fun observeSyncSnapshots(): Flow<List<SyncSnapshot>>
    suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot>
    fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>>
    suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry>

    suspend fun apiTest(): Result<JsonObject>
    suspend fun apiBind(platform: String, platformId: String, token: String): Result<JsonObject>
    suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject>
    suspend fun apiGetSingleSave(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject>
    suspend fun apiGetSave(platform: String, platformId: String): Result<JsonObject>
    suspend fun apiGetSaveInfo(platform: String, platformId: String): Result<JsonObject>
    suspend fun apiGetRank(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject>
    suspend fun apiGetAvgAcc(songId: String, difficulty: String, minRks: Float? = null, maxRks: Float? = null): Result<JsonObject>
    suspend fun apiGetAllAvgAcc(songIds: List<String>): Result<JsonObject>
    suspend fun apiGetApFcTotal(songId: String): Result<JsonObject>
    suspend fun apiGetRksStats(): Result<JsonObject>
    suspend fun apiGetRksAbove(rks: Float): Result<JsonObject>
    suspend fun apiGetSaveHistory(platform: String, platformId: String, request: List<String> = emptyList()): Result<JsonObject>
    suspend fun apiGetScoreHistory(platform: String, platformId: String, songId: String? = null, difficulty: String? = null): Result<JsonObject>
    suspend fun apiGetRankByUser(platform: String, platformId: String): Result<JsonObject>
    suspend fun apiGetRankByPosition(position: Int): Result<JsonObject>

    suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<GitHubRelease>
}
