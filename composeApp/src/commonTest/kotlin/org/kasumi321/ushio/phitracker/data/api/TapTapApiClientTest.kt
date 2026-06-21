package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.kasumi321.ushio.phitracker.domain.model.Server
import kotlin.test.Test
import kotlin.test.assertEquals

class TapTapApiClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun getGameSavesRestrictsQueryToCurrentUser() = runTest {
        var captured: CapturedRequest? = null
        val engine = MockEngine { request ->
            captured = CapturedRequest(
                path = request.url.encodedPath,
                session = request.headers["X-LC-Session"],
                where = request.url.parameters["where"],
                order = request.url.parameters["order"],
                limit = request.url.parameters["limit"]
            )
            respond(
                content = """
                    {
                      "results": [
                        {
                          "objectId": "save-id",
                          "summary": "summary-data",
                          "updatedAt": "2026-06-14T00:00:00.000Z",
                          "gameFile": {
                            "objectId": "file-id",
                            "url": "https://example.test/save.zip"
                          },
                          "user": {
                            "__type": "Pointer",
                            "className": "_User",
                            "objectId": "user-id"
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(this@TapTapApiClientTest.json) }
        }
        val apiClient = TapTapApiClient(client)

        val response = apiClient.getGameSaves("session-token", Server.CN, "user-id")

        val request = requireNotNull(captured)
        assertEquals("/1.1/classes/_GameSave", request.path)
        assertEquals("session-token", request.session)
        assertEquals(
            """{"user":{"__type":"Pointer","className":"_User","objectId":"user-id"}}""",
            request.where
        )
        assertEquals("-updatedAt", request.order)
        assertEquals("1", request.limit)
        assertEquals("user-id", response.results.single().user?.objectId)
    }

    private data class CapturedRequest(
        val path: String,
        val session: String?,
        val where: String?,
        val order: String?,
        val limit: String?
    )
}
