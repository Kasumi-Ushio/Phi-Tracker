package org.kasumi321.ushio.phitracker.data.logging

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogRedactorTest {

    @Test
    fun `redacts sessionToken in key=value format`() {
        val input = "Error: sessionToken=abc123def456"
        val result = LogRedactor.redact(input)
        assertContains(result, "sessionToken=<redacted>")
        assertFalse(result.contains("abc123def456"))
    }

    @Test
    fun `redacts sessionToken in key=value with spaces`() {
        val input = "Error: sessionToken = abc123def456"
        val result = LogRedactor.redact(input)
        assertContains(result, "sessionToken=<redacted>")
        assertFalse(result.contains("abc123def456"))
    }

    @Test
    fun `redacts sessionToken in key colon value header style`() {
        val input = "X-LC-Session: r_abc123xyz"
        val result = LogRedactor.redact(input)
        assertContains(result, "X-LC-Session: <redacted>")
        assertFalse(result.contains("r_abc123xyz"))
    }

    @Test
    fun `redacts sessionToken in JSON format`() {
        val input = """{"error":"unauthorized","sessionToken":"r_abc123"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"sessionToken":"<redacted>"""")
        assertFalse(result.contains("r_abc123"))
    }

    @Test
    fun `redacts X-LC-Session header`() {
        val input = "Request failed: X-LC-Session: abc123-session-id"
        val result = LogRedactor.redact(input)
        assertContains(result, "X-LC-Session: <redacted>")
        assertFalse(result.contains("abc123-session-id"))
    }

    @Test
    fun `redacts X-LC-Session compact header`() {
        val input = "X-LC-Session:abc123"
        val result = LogRedactor.redact(input)
        assertContains(result, "X-LC-Session: <redacted>")
        assertFalse(result.contains("abc123"))
    }

    @Test
    fun `redacts X-LC-Session equals format`() {
        val input = "X-LC-Session=abc123"
        val result = LogRedactor.redact(input)
        assertContains(result, "X-LC-Session=<redacted>")
        assertFalse(result.contains("abc123"))
    }

    @Test
    fun `redacts platformId in key=value format`() {
        val input = "platformId=test-platform-001"
        val result = LogRedactor.redact(input)
        assertContains(result, "platformId=<redacted>")
        assertFalse(result.contains("test-platform-001"))
    }

    @Test
    fun `redacts platform_id in key=value format`() {
        val input = "platform_id=android_arm64"
        val result = LogRedactor.redact(input)
        assertContains(result, "platform_id=<redacted>")
        assertFalse(result.contains("android_arm64"))
    }

    @Test
    fun `redacts platform-id in key=value format`() {
        val input = "platform-id=ios-17"
        val result = LogRedactor.redact(input)
        assertContains(result, "platform-id=<redacted>")
        assertFalse(result.contains("ios-17"))
    }

    @Test
    fun `redacts access_token in key=value format`() {
        val input = "OAuth error: access_token=ya29.abcdefghijk"
        val result = LogRedactor.redact(input)
        assertContains(result, "access_token=<redacted>")
        assertFalse(result.contains("ya29.abcdefghijk"))
    }

    @Test
    fun `redacts mac_key in key=value format`() {
        val input = "mac_key=deadbeef-cafe-1234"
        val result = LogRedactor.redact(input)
        assertContains(result, "mac_key=<redacted>")
        assertFalse(result.contains("deadbeef-cafe-1234"))
    }

    @Test
    fun `redacts Authorization Bearer header preserving scheme`() {
        val input = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0"
        val result = LogRedactor.redact(input)
        assertContains(result, "Authorization: Bearer <redacted>")
        assertFalse(result.contains("eyJhbGci"))
    }

    @Test
    fun `redacts Authorization MAC header consuming all parameters`() {
        val input = "Authorization: MAC id=\"user\", nonce=\"abc123\", mac=\"deadbeef\""
        val result = LogRedactor.redact(input)
        assertEquals("Authorization: MAC <redacted>", result)
    }

    @Test
    fun `redacts standalone Bearer token`() {
        val input = "Failed after Bearer eyJhbGciOiJSUzI1NiJ9.token.payload"
        val result = LogRedactor.redact(input)
        assertContains(result, "Bearer <redacted>")
        assertFalse(result.contains("eyJhbGci"))
    }

    @Test
    fun `does not double-redact Bearer token inside Authorization header`() {
        val input = "Authorization: Bearer eyJtoken123"
        val result = LogRedactor.redact(input)
        assertEquals("Authorization: Bearer <redacted>", result)
    }

    @Test
    fun `preserves tokenPresent equals true`() {
        val input = "tokenPresent=true"
        val result = LogRedactor.redact(input)
        assertEquals("tokenPresent=true", result)
    }

    @Test
    fun `preserves tokenPresent equals false`() {
        val input = "tokenPresent=false"
        val result = LogRedactor.redact(input)
        assertEquals("tokenPresent=false", result)
    }

    @Test
    fun `preserves tokenPresent in compound event string`() {
        val input = "[event] category=auth name=login sessionToken=<redacted> tokenPresent=true"
        val result = LogRedactor.redact(input)
        assertContains(result, "tokenPresent=true")
        assertContains(result, "sessionToken=<redacted>")
    }

    @Test
    fun `does not redact sessionToken as plain word without value`() {
        val input = "Failed to validate sessionToken for this request"
        val result = LogRedactor.redact(input)
        assertEquals(input, result)
    }

    @Test
    fun `does not redact platformId in descriptive text without separator`() {
        val input = "The platformId must be provided"
        val result = LogRedactor.redact(input)
        assertEquals(input, result)
    }

    @Test
    fun `redacts multiple sensitive patterns in one string`() {
        val input = "Error: sessionToken=abc X-LC-Session: def access_token=ghi"
        val result = LogRedactor.redact(input)
        assertTrue(result.contains("sessionToken=<redacted>"))
        assertTrue(result.contains("X-LC-Session: <redacted>"))
        assertTrue(result.contains("access_token=<redacted>"))
        assertFalse(result.contains("abc"))
        assertFalse(result.contains("def"))
        assertFalse(result.contains("ghi"))
    }

    @Test
    fun `redacts throwable message with sensitive data`() {
        val input = """
            com.example.ApiException: HTTP 401 Unauthorized
            Response: {"error":"invalid sessionToken","sessionToken":"r_deadbeef"}
            at com.example.Api.call(Api.kt:42)
        """.trimIndent()
        val result = LogRedactor.redact(input)
        assertContains(result, """"sessionToken":"<redacted>"""")
        assertFalse(result.contains("r_deadbeef"))
    }

    @Test
    fun `returns empty string unchanged`() {
        assertEquals("", LogRedactor.redact(""))
    }

    @Test
    fun `returns non-sensitive text unchanged`() {
        val input = "User logged in successfully"
        assertEquals(input, LogRedactor.redact(input))
    }

    @Test
    fun `redacts JSON request body containing sessionToken`() {
        val input = """{"body":{"sessionToken":"r_abc123","platform":"TapTap"},"url":"https://api.example.com/sync"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"sessionToken":"<redacted>"""")
        assertFalse(result.contains("r_abc123"))
    }

    @Test
    fun `redacts TapTap resource URL parameters with access tokens`() {
        val input = "GET https://api.taptap.com/resource?access_token=ya29.abcdef"
        val result = LogRedactor.redact(input)
        assertContains(result, "access_token=<redacted>")
        assertFalse(result.contains("ya29.abcdef"))
    }

    @Test
    fun `case insensitive key matching`() {
        val input = "SESSIONTOKEN=UPPERCASE sessiontoken=lowercase SessionToken=MixedCase"
        val result = LogRedactor.redact(input)
        assertContains(result, "SESSIONTOKEN=<redacted>")
        assertContains(result, "sessiontoken=<redacted>")
        assertContains(result, "SessionToken=<redacted>")
    }

    // ── Beta5-sensitive: client_id ─────────────────────────────────

    @Test
    fun `redacts client_id in key=value format`() {
        val input = "client_id=tap_abc123def456"
        val result = LogRedactor.redact(input)
        assertContains(result, "client_id=<redacted>")
        assertFalse(result.contains("tap_abc123def456"))
    }

    @Test
    fun `redacts client_id in key colon value header style`() {
        val input = "client_id: tap_xyz789"
        val result = LogRedactor.redact(input)
        assertContains(result, "client_id: <redacted>")
        assertFalse(result.contains("tap_xyz789"))
    }

    @Test
    fun `redacts client_id in JSON format`() {
        val input = """{"client_id":"tap_app_001","platform":"TapTap"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"client_id":"<redacted>"""")
        assertFalse(result.contains("tap_app_001"))
    }

    @Test
    fun `redacts client_id in query parameter`() {
        val input = "GET /auth?client_id=tap_app_001&response_type=code"
        val result = LogRedactor.redact(input)
        assertContains(result, "client_id=<redacted>")
        assertFalse(result.contains("tap_app_001"))
    }

    // ── Beta5-sensitive: device_id ─────────────────────────────────

    @Test
    fun `redacts device_id in key=value format`() {
        val input = "device_id=DEADBEEF-CAFE-1234-5678-ABCDEF012345"
        val result = LogRedactor.redact(input)
        assertContains(result, "device_id=<redacted>")
        assertFalse(result.contains("DEADBEEF-CAFE-1234-5678-ABCDEF012345"))
    }

    @Test
    fun `preserves X-Device-ID header since it differs from device_id`() {
        val input = "X-Device-ID: device-uuid-001"
        val result = LogRedactor.redact(input)
        assertEquals(input, result)
    }

    @Test
    fun `redacts bare device_id in key colon value style`() {
        val input = "device_id: device-uuid-001"
        val result = LogRedactor.redact(input)
        assertContains(result, "device_id: <redacted>")
        assertFalse(result.contains("device-uuid-001"))
    }

    @Test
    fun `redacts device_id in JSON format`() {
        val input = """{"device_id":"uuid-abc-123","os":"iOS"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"device_id":"<redacted>"""")
        assertFalse(result.contains("uuid-abc-123"))
    }

    @Test
    fun `redacts device_id in query parameter`() {
        val input = "POST /sync?device_id=uuid-abc-123&data=1"
        val result = LogRedactor.redact(input)
        assertContains(result, "device_id=<redacted>")
        assertFalse(result.contains("uuid-abc-123"))
    }

    // ── Beta5-sensitive: kid (key identifier) ──────────────────────

    @Test
    fun `redacts kid in key=value format`() {
        val input = "kid=rsa-key-2024-001"
        val result = LogRedactor.redact(input)
        assertContains(result, "kid=<redacted>")
        assertFalse(result.contains("rsa-key-2024-001"))
    }

    @Test
    fun `redacts kid in key colon value header style`() {
        val input = "kid: key-identifier-xyz"
        val result = LogRedactor.redact(input)
        assertContains(result, "kid: <redacted>")
        assertFalse(result.contains("key-identifier-xyz"))
    }

    @Test
    fun `redacts kid in JSON format`() {
        val input = """{"kid":"key-2024-rsa","alg":"RS256"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"kid":"<redacted>"""")
        assertFalse(result.contains("key-2024-rsa"))
    }

    @Test
    fun `redacts kid in query parameter`() {
        val input = "GET /jwks?kid=key-2024-rsa"
        val result = LogRedactor.redact(input)
        assertContains(result, "kid=<redacted>")
        assertFalse(result.contains("key-2024-rsa"))
    }

    // ── Beta5: platformId in JSON format ───────────────────────────

    @Test
    fun `redacts platformId in JSON format`() {
        val input = """{"platformId":"tap-ios-17","userId":"u_123"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"platformId":"<redacted>"""")
        assertFalse(result.contains("tap-ios-17"))
    }

    // ── Beta5: platform_id in query parameter ──────────────────────

    @Test
    fun `redacts platform_id in query parameter`() {
        val input = "GET /api/data?platform_id=android_arm64&v=2"
        val result = LogRedactor.redact(input)
        assertContains(result, "platform_id=<redacted>")
        assertFalse(result.contains("android_arm64"))
    }

    // ── Beta5: access_token in JSON format ─────────────────────────

    @Test
    fun `redacts access_token in JSON format`() {
        val input = """{"access_token":"ya29.abcdefghijk","token_type":"Bearer"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"access_token":"<redacted>"""")
        assertFalse(result.contains("ya29.abcdefghijk"))
    }

    // ── Beta5: sessionToken in query parameter ─────────────────────

    @Test
    fun `redacts sessionToken in query parameter`() {
        val input = "GET /data?sessionToken=r_abc123def&limit=10"
        val result = LogRedactor.redact(input)
        assertContains(result, "sessionToken=<redacted>")
        assertFalse(result.contains("r_abc123def"))
    }

    // ── Beta5: compound TapTap request body ────────────────────────

    @Test
    fun `redacts compound TapTap request body with multiple beta5 keys`() {
        val input = """{"client_id":"tap_app","device_id":"dev_uuid","kid":"k1","sessionToken":"r_stok","access_token":"at1","platformId":"ios"}"""
        val result = LogRedactor.redact(input)
        assertContains(result, """"client_id":"<redacted>"""")
        assertContains(result, """"device_id":"<redacted>"""")
        assertContains(result, """"kid":"<redacted>"""")
        assertContains(result, """"sessionToken":"<redacted>"""")
        assertContains(result, """"access_token":"<redacted>"""")
        assertContains(result, """"platformId":"<redacted>"""")
        assertFalse(result.contains("tap_app"))
        assertFalse(result.contains("dev_uuid"))
        assertFalse(result.contains("r_stok"))
        assertFalse(result.contains("at1"))
    }
}
