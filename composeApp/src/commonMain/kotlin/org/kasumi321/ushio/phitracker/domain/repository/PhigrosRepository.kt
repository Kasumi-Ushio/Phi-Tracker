package org.kasumi321.ushio.phitracker.domain.repository

import kotlinx.coroutines.flow.Flow
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.UserProfile

interface PhigrosRepository {
    suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile>
    suspend fun syncSave(sessionToken: String, server: Server): Result<Save>
    fun getCachedSave(): Flow<Save?>
    fun getUserProfile(): Flow<UserProfile?>
    suspend fun saveSessionToken(token: String, server: Server)
    suspend fun getSessionToken(): Pair<String, Server>?
    suspend fun clearData()
}
