package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.kasumi321.ushio.phitracker.domain.model.Server

class TapTapApiClient(
    private val httpClient: HttpClient
) {

    private fun buildHeaders(
        sessionToken: String,
        server: Server
    ): Map<String, String> = mapOf(
        "X-LC-Id" to TapTapConstants.lcClientId(server),
        "X-LC-Key" to TapTapConstants.lcAppKey(server),
        "X-LC-Session" to sessionToken
    )

    suspend fun getUserInfo(sessionToken: String, server: Server): UserInfoResponse {
        val baseUrl = TapTapConstants.baseUrl(server)
        return httpClient.get(baseUrl + TapTapConstants.Endpoints.USERS_ME) {
            buildHeaders(sessionToken, server).forEach { (k, v) -> header(k, v) }
        }.body()
    }

    suspend fun getGameSaves(sessionToken: String, server: Server): GameSaveListResponse {
        val baseUrl = TapTapConstants.baseUrl(server)
        return httpClient.get(baseUrl + TapTapConstants.Endpoints.GAME_SAVE) {
            buildHeaders(sessionToken, server).forEach { (k, v) -> header(k, v) }
        }.body()
    }

    suspend fun downloadSave(url: String): ByteArray {
        return httpClient.get(url).body()
    }
}

@Serializable
data class UserInfoResponse(
    val objectId: String,
    val nickname: String
)

@Serializable
data class GameSaveListResponse(
    val results: List<GameSaveItem>
)

@Serializable
data class GameSaveItem(
    val objectId: String,
    val summary: String,
    val updatedAt: String,
    val gameFile: GameFileRef
)

@Serializable
data class GameFileRef(
    @SerialName("objectId")
    val id: String,
    val url: String
)
