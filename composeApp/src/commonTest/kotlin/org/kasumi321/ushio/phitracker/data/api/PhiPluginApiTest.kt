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
