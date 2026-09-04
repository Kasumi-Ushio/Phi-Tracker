package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put

class PhiPluginApi(
    private val httpClient: HttpClient
) {
    private companion object {
        const val BASE_URL = "https://phib19.top:8080"
    }

    private fun authBody(platform: String, platformId: String): JsonObject = buildJsonObject {
        put("platform", platform)
        put("platform_id", platformId)
    }

    private fun userAuthBody(platform: String, platformId: String, apiUserId: String): JsonObject = buildJsonObject {
        put("platform", platform)
        put("platform_id", platformId)
        put("api_user_id", apiUserId)
    }

    private inline fun <reified T> HttpRequestBuilder.setJsonBody(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    suspend fun test(): JsonObject = httpClient.get("$BASE_URL/status").body()

    suspend fun bind(request: BindRequest): JsonObject = httpClient.post("$BASE_URL/bind") {
        setJsonBody(request)
    }.body()

    suspend fun getBindInfo(platform: String, platformId: String): JsonObject = httpClient.post("$BASE_URL/token/list") {
        setJsonBody(authBody(platform, platformId))
    }.body()

    suspend fun getSingleSave(platform: String, platformId: String, songId: String, difficulty: String): JsonObject =
        httpClient.post("$BASE_URL/get/cloud/song") {
            setJsonBody(buildJsonObject {
                put("platform", platform)
                put("platform_id", platformId)
                put("song_id", songId)
                put("difficulty", difficulty)
            })
        }.body()

    suspend fun getSave(platform: String, platformId: String): JsonObject = httpClient.post("$BASE_URL/get/cloud/saves") {
        setJsonBody(authBody(platform, platformId))
    }.body()

    suspend fun getSaveInfo(platform: String, platformId: String): JsonObject = httpClient.post("$BASE_URL/get/cloud/saveInfo") {
        setJsonBody(authBody(platform, platformId))
    }.body()

    suspend fun getRank(
        platform: String,
        platformId: String,
        apiUserId: String,
        songId: String,
        difficulty: String
    ): JsonObject =
        httpClient.post("$BASE_URL/get/scoreList/user") {
            setJsonBody(buildJsonObject {
                put("platform", platform)
                put("platform_id", platformId)
                put("api_user_id", apiUserId)
                put("songId", songId)
                put("rank", difficulty)
                put("orderBy", "acc")
            })
        }.body()

    suspend fun getAvgAcc(songId: String, difficulty: String, minRks: Float? = null, maxRks: Float? = null): JsonObject =
        httpClient.post("$BASE_URL/get/scoreList/songAccAvg") {
            setJsonBody(buildJsonObject {
                put("songId", songId)
                put("rank", difficulty)
                if (minRks != null) put("minRks", minRks)
                if (maxRks != null) put("maxRks", maxRks)
            })
        }.body()

    suspend fun getAllAvgAcc(songIds: List<String> = emptyList()): JsonObject = httpClient.post("$BASE_URL/get/scoreList/allAccAvg") {
        setJsonBody(buildJsonObject {
            putJsonArray("songIds") {
                songIds.forEach { add(JsonPrimitive(it)) }
            }
        })
    }.body()

    suspend fun getApFcTotal(songId: String): JsonObject = httpClient.post("$BASE_URL/get/scoreList/songApFcCount") {
        setJsonBody(buildJsonObject {
            put("songId", songId)
        })
    }.body()

    suspend fun getRksStats(): JsonObject = httpClient.post("$BASE_URL/get/ranklist/stats").body()

    suspend fun getRksAbove(rks: Float): JsonObject =
        httpClient.post("$BASE_URL/get/ranklist/rksRank") {
            setJsonBody(buildJsonObject {
                put("request_rks", rks)
            })
        }.body()

    suspend fun getSaveHistory(
        platform: String,
        platformId: String,
        apiUserId: String,
        request: List<String> = emptyList()
    ): JsonObject =
        httpClient.post("$BASE_URL/get/history/history") {
            setJsonBody(buildJsonObject {
                put("platform", platform)
                put("platform_id", platformId)
                put("api_user_id", apiUserId)
                if (request.isNotEmpty()) {
                    putJsonArray("request") {
                        request.forEach { add(JsonPrimitive(it)) }
                    }
                }
            })
        }.body()

    suspend fun getScoreHistory(
        platform: String,
        platformId: String,
        apiUserId: String,
        songId: String? = null,
        difficulty: String? = null
    ): JsonObject = httpClient.post("$BASE_URL/get/history/record") {
        setJsonBody(buildJsonObject {
            put("platform", platform)
            put("platform_id", platformId)
            put("api_user_id", apiUserId)
            if (!songId.isNullOrBlank()) {
                put("song_id", songId)
            }
            if (!difficulty.isNullOrBlank()) {
                put("rank", difficulty)
            }
        })
    }.body()

    suspend fun getRankByUser(platform: String, platformId: String, apiUserId: String): JsonObject =
        httpClient.post("$BASE_URL/get/ranklist/user") {
            setJsonBody(userAuthBody(platform, platformId, apiUserId))
        }.body()

    suspend fun getRankByPosition(position: Int): JsonObject =
        httpClient.post("$BASE_URL/get/ranklist/rank") {
            setJsonBody(buildJsonObject {
                put("request_rank", position)
            })
        }.body()

    // ── Chart tags (chartsTag) ──────────────────────────────────────
    // Read endpoints are public; usersVote needs the platform identity
    // triplet; set/set always requires an api_token.

    suspend fun getChartTagTree(): JsonObject = httpClient.get("$BASE_URL/chartsTag/get/tagTree").body()

    suspend fun getChartTags(songId: String, difficulty: String): JsonObject =
        httpClient.post("$BASE_URL/chartsTag/get/bySongRank") {
            setJsonBody(buildJsonObject {
                put("song_id", songId)
                put("rank", difficulty)
            })
        }.body()

    suspend fun getChartsTagsBatch(requests: List<Pair<String, List<String>>>): JsonObject =
        httpClient.post("$BASE_URL/chartsTag/get/chartsTags") {
            setJsonBody(buildJsonObject {
                putJsonArray("data") {
                    requests.forEach { (songId, ranks) ->
                        add(buildJsonObject {
                            put("song_id", songId)
                            putJsonArray("rank") {
                                ranks.forEach { add(JsonPrimitive(it)) }
                            }
                        })
                    }
                }
            })
        }.body()

    suspend fun getMyChartTagVotes(
        platform: String,
        platformId: String,
        apiUserId: String,
        apiToken: String?,
        songs: List<Pair<String, String>>
    ): JsonObject = httpClient.post("$BASE_URL/chartsTag/get/usersVote") {
        setJsonBody(buildJsonObject {
            put("platform", platform)
            put("platform_id", platformId)
            put("api_user_id", apiUserId)
            if (!apiToken.isNullOrBlank()) put("api_token", apiToken)
            putJsonArray("data") {
                songs.forEach { (songId, rank) ->
                    add(buildJsonObject {
                        put("song_id", songId)
                        put("rank", rank)
                    })
                }
            }
        })
    }.body()

    suspend fun setChartsTag(
        platform: String,
        platformId: String,
        apiUserId: String,
        apiToken: String,
        songId: String,
        difficulty: String,
        primaryTags: List<String>,
        secondaryTags: List<String>
    ): JsonObject = httpClient.post("$BASE_URL/chartsTag/set/set") {
        setJsonBody(buildJsonObject {
            put("platform", platform)
            put("platform_id", platformId)
            put("api_user_id", apiUserId)
            put("api_token", apiToken)
            put("song_id", songId)
            put("rank", difficulty)
            putJsonArray("primaryTags") {
                primaryTags.forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("secondaryTags") {
                secondaryTags.forEach { add(JsonPrimitive(it)) }
            }
        })
    }.body()
}
