package org.kasumi321.ushio.phitracker.data.repository

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubReleaseResponseParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun stableSelectionMapsDtoAndPreservesNullableBody() {
        val result = parseGitHubReleaseResponse(
            HttpStatusCode.OK,
            """
                [
                  {"tag_name":"v2.0.0-beta","html_url":"https://example.test/beta","prerelease":true,"body":"beta"},
                  {"tag_name":"v1.2.3","html_url":"https://example.test/stable","prerelease":false}
                ]
            """.trimIndent(),
            includePreRelease = false,
            json = json,
        )

        assertTrue(result.isSuccess)
        assertEquals("v1.2.3", result.getOrThrow().tagName)
        assertEquals("https://example.test/stable", result.getOrThrow().htmlUrl)
        assertFalse(result.getOrThrow().prerelease)
        assertEquals(null, result.getOrThrow().body)
    }

    @Test
    fun prereleaseSelectionAllowsFirstPrereleaseCandidate() {
        val result = parseGitHubReleaseResponse(
            HttpStatusCode.OK,
            """
                [
                  {"tag_name":"v2.0.0-beta","html_url":"https://example.test/beta","prerelease":true,"body":"beta"},
                  {"tag_name":"v1.2.3","html_url":"https://example.test/stable","prerelease":false,"body":"stable"}
                ]
            """.trimIndent(),
            includePreRelease = true,
            json = json,
        )

        assertEquals("v2.0.0-beta", result.getOrThrow().tagName)
        assertEquals("beta", result.getOrThrow().body)
    }

    @Test
    fun malformedRequiredFieldReturnsFailure() {
        val result = parseGitHubReleaseResponse(
            HttpStatusCode.OK,
            "[{\"html_url\":\"https://example.test\",\"prerelease\":false}]",
            includePreRelease = false,
            json = json,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun noCandidateReturnsFailure() {
        val result = parseGitHubReleaseResponse(
            HttpStatusCode.OK,
            "[{\"tag_name\":\"v1\",\"html_url\":\"https://example.test\",\"prerelease\":true}]",
            includePreRelease = false,
            json = json,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun httpAndRateLimitFailuresReturnFailure() {
        val httpFailure = parseGitHubReleaseResponse(
            HttpStatusCode.ServiceUnavailable,
            "{}",
            includePreRelease = false,
            json = json,
        )
        val rateLimitFailure = parseGitHubReleaseResponse(
            HttpStatusCode.Forbidden,
            "{\"message\":\"API rate limit exceeded\"}",
            includePreRelease = false,
            json = json,
        )

        assertTrue(httpFailure.isFailure)
        assertTrue(rateLimitFailure.isFailure)
    }
}
