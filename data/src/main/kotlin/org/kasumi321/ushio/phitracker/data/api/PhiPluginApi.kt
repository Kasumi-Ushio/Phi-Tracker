package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhiPluginApi @Inject constructor(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://phib19.top:8080"
    }

    private fun authBody(platform: String, platformId: String): JsonObject = buildJsonObject {
        put("platform", platform)
        put("platform_id", platformId)
    }

    suspend fun test(): JsonObject = httpClient.get("$BASE_URL/status").body()

    suspend fun bind(request: BindRequest): JsonObject = httpClient.post("$BASE_URL/bind") {
        setBody(request)
    }.body()

    suspend fun getBindInfo(platform: String, platformId: String): JsonObject = httpClient.post("$BASE_URL/token/list") {
        setBody(authBody(platform, platformId))
    }.body()

    suspend fun getSingleSave(platform: String, platformId: String, songId: String, difficulty: String): JsonObject =
        httpClient.post("$BASE_URL/get/cloud/song") {
            setBody(
                buildJsonObject {
                    put("platform", platform)
                    put("platform_id", platformId)
                    put("song_id", songId)
                    put("difficulty", difficulty)
                }
            )
        }.body()

    suspend fun getSave(platform: String, platformId: String): JsonObject = httpClient.post("$BASE_URL/get/cloud/saves") {
        setBody(authBody(platform, platformId))
    }.body()

    suspend fun getSaveInfo(platform: String, platformId: String): JsonObject = httpClient.post("$BASE_URL/get/cloud/saveInfo") {
        setBody(authBody(platform, platformId))
    }.body()

    suspend fun getRank(platform: String, platformId: String, songId: String, difficulty: String): JsonObject =
        httpClient.post("$BASE_URL/get/scoreList/user") {
            setBody(
                buildJsonObject {
                    put("platform", platform)
                    put("platform_id", platformId)
                    put("songId", songId)
                    put("rank", difficulty)
                }
            )
        }.body()

    suspend fun getAvgAcc(songId: String, difficulty: String, minRks: Float? = null, maxRks: Float? = null): JsonObject =
        httpClient.post("$BASE_URL/get/scoreList/songAccAvg") {
            setBody(
                buildJsonObject {
                    put("songId", songId)
                    put("rank", difficulty)
                    if (minRks != null) put("minRks", minRks)
                    if (maxRks != null) put("maxRks", maxRks)
                }
            )
        }.body()

    suspend fun getAllAvgAcc(songIds: List<String> = emptyList()): JsonObject = httpClient.post("$BASE_URL/get/scoreList/allAccAvg") {
        setBody(
            buildJsonObject {
                putJsonArray("songIds") {
                    songIds.forEach { add(JsonPrimitive(it)) }
                }
            }
        )
    }.body()

    suspend fun getApFcTotal(songId: String): JsonObject = httpClient.post("$BASE_URL/get/scoreList/songApFcCount") {
        setBody(
            buildJsonObject {
                put("songId", songId)
            }
        )
    }.body()

    suspend fun getFittedDifficulty(songId: String, difficulty: String): JsonObject =
        httpClient.post("$BASE_URL/get/scoreList/difficultyFit") {
            setBody(
                buildJsonObject {
                    put("charts", JsonArray(listOf(buildJsonObject {
                        put("songId", songId)
                        put("rank", JsonArray(listOf(JsonPrimitive(difficulty))))
                    })))
                }
            )
        }.body()

    suspend fun getRksStats(): JsonObject = httpClient.post("$BASE_URL/get/ranklist/stats").body()

    suspend fun getRksAbove(rks: Float): JsonObject =
        httpClient.post("$BASE_URL/get/ranklist/rksRank") {
            setBody(
                buildJsonObject {
                    put("request_rks", rks)
                }
            )
        }.body()

    suspend fun getSaveHistory(platform: String, platformId: String, request: List<String> = emptyList()): JsonObject =
        httpClient.post("$BASE_URL/get/history/history") {
            setBody(
                buildJsonObject {
                    put("platform", platform)
                    put("platform_id", platformId)
                    if (request.isNotEmpty()) {
                        putJsonArray("request") {
                            request.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
            )
        }.body()

    suspend fun getScoreHistory(
        platform: String,
        platformId: String,
        songId: String? = null,
        difficulty: String? = null
    ): JsonObject = httpClient.post("$BASE_URL/get/history/record") {
        setBody(
            buildJsonObject {
                put("platform", platform)
                put("platform_id", platformId)
                if (!songId.isNullOrBlank()) {
                    put("song_id", songId)
                }
                if (!difficulty.isNullOrBlank()) {
                    put("rank", difficulty)
                }
            }
        )
    }.body()

    suspend fun getRankByUser(platform: String, platformId: String): JsonObject = httpClient.post("$BASE_URL/get/ranklist/user") {
        setBody(authBody(platform, platformId))
    }.body()

    suspend fun getRankByPosition(position: Int): JsonObject =
        httpClient.post("$BASE_URL/get/ranklist/rank") {
            setBody(
                buildJsonObject {
                    put("request_rank", position)
                }
            )
        }.body()
}
