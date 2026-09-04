package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhiPluginApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testUsesStatusEndpoint() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.test()

        assertEquals("GET", requests.single().method)
        assertEquals("/status", requests.single().path)
    }

    @Test
    fun getRksStatsUsesPublicStatsEndpoint() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getRksStats()

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/get/ranklist/stats", request.path)
        assertEquals("", request.body)
    }

    @Test
    fun getSongRankUsesAuthenticatedUserEndpoint() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getRank("qq", "platform-42", "api-7", "song.0", "IN")

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/get/scoreList/user", request.path)
        assertTrue(request.body.contains("\"platform\":\"qq\""), request.body)
        assertTrue(request.body.contains("\"platform_id\":\"platform-42\""), request.body)
        assertTrue(request.body.contains("\"api_user_id\":\"api-7\""), request.body)
        assertTrue(request.body.contains("\"songId\":\"song.0\""), request.body)
        assertTrue(request.body.contains("\"rank\":\"IN\""), request.body)
        assertTrue(request.body.contains("\"orderBy\":\"acc\""), request.body)
    }

    @Test
    fun getRankByUserIncludesApiUserIdAuthentication() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getRankByUser("qq", "platform-42", "api-7")

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/get/ranklist/user", request.path)
        assertTrue(request.body.contains("\"platform\":\"qq\""), request.body)
        assertTrue(request.body.contains("\"platform_id\":\"platform-42\""), request.body)
        assertTrue(request.body.contains("\"api_user_id\":\"api-7\""), request.body)
    }

    @Test
    fun getRankByPositionUsesPublicRankEndpointAndRequestRank() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getRankByPosition(100)

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/get/ranklist/rank", request.path)
        assertTrue(request.body.contains("\"request_rank\":100"), request.body)
    }

    @Test
    fun getRksAboveUsesPublicRksRankEndpointAndRequestRks() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getRksAbove(15.25f)

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/get/ranklist/rksRank", request.path)
        assertTrue(request.body.contains("\"request_rks\":15.25"), request.body)
    }

    @Test
    fun getChartTagTreeUsesPublicTagTreeEndpoint() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getChartTagTree()

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/chartsTag/get/tagTree", request.path)
    }

    @Test
    fun getChartTagsUsesPublicBySongRankEndpoint() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getChartTags("song.0", "IN")

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/chartsTag/get/bySongRank", request.path)
        assertTrue(request.body.contains("\"song_id\":\"song.0\""), request.body)
        assertTrue(request.body.contains("\"rank\":\"IN\""), request.body)
    }

    @Test
    fun getChartsTagsBatchPostsSongRankPairs() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getChartsTagsBatch(listOf("song-a.0" to listOf("IN", "AT"), "song-b.0" to listOf("HD")))

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/chartsTag/get/chartsTags", request.path)
        assertTrue(request.body.contains("\"song_id\":\"song-a.0\""), request.body)
        assertTrue(request.body.contains("\"song_id\":\"song-b.0\""), request.body)
        assertTrue(request.body.contains("\"IN\""), request.body)
        assertTrue(request.body.contains("\"AT\""), request.body)
        assertTrue(request.body.contains("\"HD\""), request.body)
    }

    @Test
    fun getMyChartTagVotesIncludesIdentityTripletAndOptionalToken() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getMyChartTagVotes("taptap", "player-id", "api-user", "token-1", listOf("song.0" to "IN"))

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/chartsTag/get/usersVote", request.path)
        assertTrue(request.body.contains("\"platform\":\"taptap\""), request.body)
        assertTrue(request.body.contains("\"platform_id\":\"player-id\""), request.body)
        assertTrue(request.body.contains("\"api_user_id\":\"api-user\""), request.body)
        assertTrue(request.body.contains("\"api_token\":\"token-1\""), request.body)
        assertTrue(request.body.contains("\"song_id\":\"song.0\""), request.body)
        assertTrue(request.body.contains("\"rank\":\"IN\""), request.body)
    }

    @Test
    fun getMyChartTagVotesOmitsBlankToken() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.getMyChartTagVotes("taptap", "player-id", "api-user", " ", listOf("song.0" to "IN"))

        assertFalse(requests.single().body.contains("api_token"), requests.single().body)
    }

    @Test
    fun setChartsTagAlwaysIncludesApiToken() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests)

        api.setChartsTag(
            platform = "taptap",
            platformId = "player-id",
            apiUserId = "api-user",
            apiToken = "token-1",
            songId = "song.0",
            difficulty = "IN",
            primaryTags = listOf("高速"),
            secondaryTags = listOf("连打")
        )

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/chartsTag/set/set", request.path)
        assertTrue(request.body.contains("\"api_token\":\"token-1\""), request.body)
        assertTrue(request.body.contains("\"song_id\":\"song.0\""), request.body)
        assertTrue(request.body.contains("\"rank\":\"IN\""), request.body)
        assertTrue(request.body.contains("\"primaryTags\":[\"高速\"]"), request.body)
        assertTrue(request.body.contains("\"secondaryTags\":[\"连打\"]"), request.body)
    }

    private fun createApi(requests: MutableList<CapturedRequest>): PhiPluginApi {
        val engine = MockEngine { request ->
            requests += CapturedRequest(
                method = request.method.value,
                path = request.url.encodedPath,
                body = request.body.toByteArray().decodeToString()
            )
            respond(
                content = "{}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(this@PhiPluginApiTest.json) }
        }
        return PhiPluginApi(client)
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val body: String
    )
}
