package org.kasumi321.ushio.phitracker.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.Summary
import org.kasumi321.ushio.phitracker.domain.model.UserProfile

interface PhigrosRepository {
    suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile>
    suspend fun syncSave(sessionToken: String, server: Server): Result<Save>
    fun getCachedSave(): Flow<Save?>
    fun getUserProfile(): Flow<UserProfile?>
    suspend fun saveSessionToken(token: String, server: Server)
    suspend fun getSessionToken(): Pair<String, Server>?
    suspend fun clearData()
    fun clearTokenSync()

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
    suspend fun apiGetFittedDifficulty(songId: String, difficulty: String): Result<JsonObject>
    suspend fun apiGetRksStats(): Result<JsonObject>
    suspend fun apiGetRksAbove(rks: Float): Result<JsonObject>
    suspend fun apiGetSaveHistory(platform: String, platformId: String, request: List<String> = emptyList()): Result<JsonObject>
    suspend fun apiGetScoreHistory(
        platform: String,
        platformId: String,
        songId: String? = null,
        difficulty: String? = null
    ): Result<JsonObject>
    suspend fun apiGetRankByUser(platform: String, platformId: String): Result<JsonObject>
    suspend fun apiGetRankByPosition(position: Int): Result<JsonObject>
}
