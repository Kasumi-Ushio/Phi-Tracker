package org.kasumi321.ushio.phitracker.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class TapTapGameApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun fetchLatestUpdateQueriesTapTapApkListingWithXUaParameter() = runTest {
        val requests = mutableListOf<CapturedRequest>()
        val api = createApi(requests, updateResponse())

        api.fetchLatestUpdate()

        val request = requests.single()
        assertEquals("GET", request.method)
        assertEquals("/apk/v1/list-by-app", request.path)
        assertEquals("165287", request.parameters["app_id"])
        // X-UA travels as one query parameter; its inner "&" must arrive
        // encoded and decode back to the raw phi-plugin value.
        assertEquals("V=1&PN=TapTap&VN_CODE=283021001&LANG=zh_CN", request.parameters["X-UA"])
        assertEquals("0", request.parameters["from"])
        assertEquals("1", request.parameters["limit"])
        assertTrue(request.encodedQuery.contains("X-UA="), request.encodedQuery)
        assertTrue(!request.encodedQuery.contains("PN=TapTap&"), request.encodedQuery)
    }

    @Test
    fun fetchLatestUpdateMapsDtoAndConvertsWhatsnewHtmlToPlainText() = runTest {
        val api = createApi(mutableListOf(), updateResponse())

        val info = api.fetchLatestUpdate()

        assertEquals("3.8.0", info.version)
        assertEquals(283021001L, info.versionCode)
        val expectedDate = Instant.fromEpochSeconds(1_774_992_000L)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        assertEquals(expectedDate, info.date)
        assertEquals("新增两首单曲\n修复已知问题\n优化体验", info.changelog)
    }

    @Test
    fun fetchLatestUpdateTrimsTrailingWhitespaceAndTrailingBlankLines() = runTest {
        val response = """
            {"success":true,"data":{"list":[
              {"version_label":"3.8.1","version_code":283021002,"update_date":1777670400,
               "whatsnew":{"text":"<div>第一行   <br/><br/></div><div>  </div>"}}
            ]}}
        """.trimIndent()
        val api = createApi(mutableListOf(), response)

        val info = api.fetchLatestUpdate()

        assertEquals("第一行", info.changelog)
    }

    @Test
    fun fetchLatestUpdateThrowsWhenTapTapReportsFailure() = runTest {
        val api = createApi(mutableListOf(), """{"success":false,"data":{"list":[]}}""")

        assertFailsWith<IllegalStateException> { api.fetchLatestUpdate() }
    }

    @Test
    fun fetchLatestUpdateThrowsWhenListIsEmpty() = runTest {
        val api = createApi(mutableListOf(), """{"success":true,"data":{"list":[]}}""")

        assertFailsWith<IllegalStateException> { api.fetchLatestUpdate() }
    }

    private fun updateResponse(): String = """
        {"success":true,"data":{"list":[
          {"version_label":"3.8.0","version_code":283021001,"update_date":1774992000,
           "whatsnew":{"text":"<div>新增两首单曲<br/>修复已知问题</div><div><br></div><div>优化体验</div>"}}
        ]}}
    """.trimIndent()

    private fun createApi(requests: MutableList<CapturedRequest>, responseText: String): TapTapGameApi {
        val engine = MockEngine { request ->
            requests += CapturedRequest(
                method = request.method.value,
                path = request.url.encodedPath,
                parameters = request.url.parameters.entries().associate { (key, values) -> key to values.single() },
                encodedQuery = request.url.encodedQuery
            )
            respond(
                content = responseText,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(this@TapTapGameApiTest.json) }
        }
        return TapTapGameApi(client)
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val parameters: Map<String, String>,
        val encodedQuery: String
    )
}
