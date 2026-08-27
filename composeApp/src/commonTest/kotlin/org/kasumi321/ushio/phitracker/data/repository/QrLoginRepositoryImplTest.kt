package org.kasumi321.ushio.phitracker.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.platform.ApiCrypto
import org.kasumi321.ushio.phitracker.domain.model.QrAuthorizationId
import org.kasumi321.ushio.phitracker.domain.model.QrChallengeId
import org.kasumi321.ushio.phitracker.domain.model.QrLoginPollResult
import org.kasumi321.ushio.phitracker.domain.model.Server
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class QrLoginRepositoryImplTest {
    @Test
    fun requestAndEveryPollStatusUseOpaqueIdsWithoutTransportLeak() = kotlinx.coroutines.test.runTest {
        var poll = 0
        val repository = repository { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":30}}"""
                )
                "/oauth2/v1/token" -> {
                    val body = (request.body as FormDataContent).formData
                    assertEquals("device-secret", body["code"])
                    jsonResponse(
                        when (poll++) {
                            0 -> """{"success":false,"data":{"error":"authorization_pending"}}"""
                            1 -> """{"success":false,"data":{"error":"authorization_waiting"}}"""
                            else -> """{"success":true,"data":{"kid":"kid","access_token":"access-secret","token_type":"bearer","mac_key":"mac-secret","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                        }
                    )
                }
                else -> error("unexpected ${request.url}")
            }
        }

        val challenge = repository.requestChallenge(Server.CN)
        assertEquals("https://qr.test/challenge", challenge.qrUrl)
        assertEquals(31_000L, challenge.expiresAt)
        assertFalse("device-secret" in challenge.id.value)
        assertEquals(QrLoginPollResult.Pending, repository.poll(challenge.id))
        assertEquals(QrLoginPollResult.AuthorizationWaiting, repository.poll(challenge.id))
        val authorized = assertIs<QrLoginPollResult.Authorized>(repository.poll(challenge.id))
        assertFalse("access-secret" in authorized.authorizationId.value)
        assertNotEquals(challenge.id.value, authorized.authorizationId.value)
        assertFailsWith<IllegalArgumentException> { repository.poll(challenge.id) }
    }

    @Test
    fun selectedServerIsPrivateAndPreservedAcrossProfileAndExchangeThenAuthorizationIsRemoved() = kotlinx.coroutines.test.runTest {
        val hosts = mutableListOf<String>()
        val repository = repository { request ->
            hosts += request.url.host
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/global","expires_in":30}}"""
                )
                "/oauth2/v1/token" -> jsonResponse(
                    """{"success":true,"data":{"kid":"kid","access_token":"access-secret","token_type":"bearer","mac_key":"mac-secret","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                )
                "/account/profile/v1" -> jsonResponse(
                    """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}"""
                )
                "/1.1/users" -> jsonResponse("""{"sessionToken":"qr-session"}""")
                else -> error("unexpected ${request.url}")
            }
        }

        val challenge = repository.requestChallenge(Server.GLOBAL)
        val authorization = assertIs<QrLoginPollResult.Authorized>(repository.poll(challenge.id))
        assertEquals("qr-session", repository.exchangeForSessionToken(authorization.authorizationId))
        assertEquals(
            listOf("accounts.tapapis.com", "accounts.tapapis.com", "open.tapapis.com", "kviehlel.cloud.ap-sg.tapapis.com"),
            hosts
        )
        assertFailsWith<IllegalArgumentException> {
            repository.exchangeForSessionToken(authorization.authorizationId)
        }
    }

    @Test
    fun unknownExpiredAndPrunedContextsFailWithoutExposingPrivateValues() = kotlinx.coroutines.test.runTest {
        var now = 1_000L
        var id = 0
        val repository = repository(now = { now }, id = { "opaque-${++id}" }) { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":1}}"""
                )
                else -> error("expired context must not call transport")
            }
        }
        val challenge = repository.requestChallenge(Server.CN)
        assertFailsWith<IllegalArgumentException> { repository.poll(QrChallengeId("unknown")) }
        assertFailsWith<IllegalArgumentException> {
            repository.exchangeForSessionToken(QrAuthorizationId("unknown"))
        }
        now = 2_000L
        val expired = assertFailsWith<IllegalArgumentException> { repository.poll(challenge.id) }
        assertFalse("device-secret" in (expired.message ?: ""))
        assertFailsWith<IllegalArgumentException> { repository.poll(challenge.id) }

        now = 3_000L
        val authorizationRepository = repository(now = { now }) { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":1}}"""
                )
                "/oauth2/v1/token" -> jsonResponse(
                    """{"success":true,"data":{"kid":"kid","access_token":"access-secret","token_type":"bearer","mac_key":"mac-secret","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                )
                else -> error("expired authorization must not call transport")
            }
        }
        val authorizationChallenge = authorizationRepository.requestChallenge(Server.CN)
        val authorization = assertIs<QrLoginPollResult.Authorized>(
            authorizationRepository.poll(authorizationChallenge.id)
        )
        now = 4_000L
        assertFailsWith<IllegalArgumentException> {
            authorizationRepository.exchangeForSessionToken(authorization.authorizationId)
        }
    }

    @Test
    fun requestAndPollTransportFailuresPropagateAtTheirPublicBoundaries() = kotlinx.coroutines.test.runTest {
        val requestFailure = repository { error("request unavailable DEVICE_REQUEST_SECRET") }
        val requestError = assertFailsWith<IllegalStateException> { requestFailure.requestChallenge(Server.CN) }
        assertEquals("QR challenge request failed", requestError.message)
        assertFalse("DEVICE_REQUEST_SECRET" in requestError.toString())

        val pollFailure = repository { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":30}}"""
                )
                "/oauth2/v1/token" -> error("poll unavailable ACCESS_POLL_SECRET")
                else -> error("unexpected ${request.url}")
            }
        }
        val challenge = pollFailure.requestChallenge(Server.CN)
        val pollError = assertFailsWith<IllegalStateException> { pollFailure.poll(challenge.id) }
        assertEquals("QR polling failed", pollError.message)
        assertFalse("ACCESS_POLL_SECRET" in pollError.toString())
    }

    @Test
    fun profileAndExchangeFailuresExposeOnlyStableCredentialFreeCategory() = kotlinx.coroutines.test.runTest {
        suspend fun authorized(repository: QrLoginRepositoryImpl): QrAuthorizationId {
            val challenge = repository.requestChallenge(Server.CN)
            return assertIs<QrLoginPollResult.Authorized>(repository.poll(challenge.id)).authorizationId
        }

        val profileFailure = repository { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":30}}"""
                )
                "/oauth2/v1/token" -> jsonResponse(
                    """{"success":true,"data":{"kid":"kid","access_token":"access-secret","token_type":"bearer","mac_key":"mac-secret","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                )
                "/account/profile/v1" -> error("profile unavailable PROFILE_OPENID_SECRET")
                else -> error("unexpected ${request.url}")
            }
        }
        val profileError = assertFailsWith<IllegalStateException> {
            profileFailure.exchangeForSessionToken(authorized(profileFailure))
        }
        assertEquals("QR authorization exchange failed", profileError.message)
        assertFalse("PROFILE_OPENID_SECRET" in profileError.toString())

        val exchangeFailure = repository { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":30}}"""
                )
                "/oauth2/v1/token" -> jsonResponse(
                    """{"success":true,"data":{"kid":"kid","access_token":"access-secret","token_type":"bearer","mac_key":"mac-secret","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                )
                "/account/profile/v1" -> jsonResponse(
                    """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}"""
                )
                "/1.1/users" -> error("exchange unavailable SESSION_EXCHANGE_SECRET")
                else -> error("unexpected ${request.url}")
            }
        }
        val exchangeError = assertFailsWith<IllegalStateException> {
            exchangeFailure.exchangeForSessionToken(authorized(exchangeFailure))
        }
        assertEquals("QR authorization exchange failed", exchangeError.message)
        assertFalse("SESSION_EXCHANGE_SECRET" in exchangeError.toString())
    }

    @Test
    fun malformedAuthorizedResultAndFailedExchangeDoNotLeaveReplayableContext() = kotlinx.coroutines.test.runTest {
        var malformed = true
        val malformedRepository = repository { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":30}}"""
                )
                "/oauth2/v1/token" -> jsonResponse(
                    if (malformed) """{"success":true}""" else """{"success":false,"data":{"error":"authorization_pending"}}"""
                )
                else -> error("unexpected ${request.url}")
            }
        }
        val malformedChallenge = malformedRepository.requestChallenge(Server.CN)
        assertFailsWith<IllegalStateException> { malformedRepository.poll(malformedChallenge.id) }
        malformed = false
        assertEquals(QrLoginPollResult.Pending, malformedRepository.poll(malformedChallenge.id))

        val failedExchangeRepository = repository { request ->
            when (request.url.encodedPath) {
                "/oauth2/v1/device/code" -> jsonResponse(
                    """{"data":{"device_code":"device-secret","qrcode_url":"https://qr.test/challenge","expires_in":30}}"""
                )
                "/oauth2/v1/token" -> jsonResponse(
                    """{"success":true,"data":{"kid":"kid","access_token":"access-secret","token_type":"bearer","mac_key":"mac-secret","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                )
                "/account/profile/v1" -> error("profile unavailable")
                else -> error("unexpected ${request.url}")
            }
        }
        val challenge = failedExchangeRepository.requestChallenge(Server.CN)
        val authorizationId = assertIs<QrLoginPollResult.Authorized>(failedExchangeRepository.poll(challenge.id)).authorizationId
        assertFailsWith<IllegalStateException> { failedExchangeRepository.exchangeForSessionToken(authorizationId) }
        assertFailsWith<IllegalArgumentException> { failedExchangeRepository.exchangeForSessionToken(authorizationId) }
    }

    private fun repository(
        now: () -> Long = { 1_000L },
        id: () -> String = generateSequence(1) { it + 1 }.map { "opaque-$it" }.iterator()::next,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): QrLoginRepositoryImpl {
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            reuseHandlers = true
            addHandler(handler)
        })) { install(ContentNegotiation) { json() } }
        return QrLoginRepositoryImpl(TapTapQrLoginApi(client, FakeApiCrypto), now, id)
    }

    private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
        body,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private object FakeApiCrypto : ApiCrypto {
        override fun md5Hex(data: String): String = "fixed-md5"
        override fun hmacSha1(data: String, key: String): ByteArray = byteArrayOf(1, 2, 3)
    }
}
