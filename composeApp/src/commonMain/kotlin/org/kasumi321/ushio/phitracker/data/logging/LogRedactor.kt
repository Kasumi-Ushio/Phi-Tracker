package org.kasumi321.ushio.phitracker.data.logging

/**
 * Shared redaction helper for log export and file-write boundaries.
 *
 * Applies a common redaction policy to arbitrary text so that sensitive
 * token/header/credential-like values are replaced with "<redacted>"
 * before they are written to disk or exported for sharing.
 *
 * Key design decisions:
 * - Operates on strings, not structured objects, so it works for raw
 *   log messages, throwable messages, stack traces, and crash reports.
 * - Multiple regex passes handle key=value, key:value, JSON-style,
 *   Authorization/Bearer, and standalone Bearer patterns.
 * - Does NOT redact boolean fields such as tokenPresent=true/false
 *   (none of the sensitive key names match "tokenPresent").
 */
object LogRedactor {

    /**
     * Sensitive key names whose associated values should be redacted.
     * All comparisons are case-insensitive via regex flags.
     */
    private val sensitiveKeyNames = setOf(
        "sessiontoken",
        "x-lc-session",
        "x-lc-id",
        "x-lc-sign",
        "platformid",
        "platform_id",
        "platform-id",
        "access_token",
        "mac_key",
    )

    /** Escaped alternation of all sensitive key names for regex use. */
    private val sensitiveKeysPattern: String =
        sensitiveKeyNames.joinToString("|") { Regex.escape(it) }

    // ── Regex patterns ────────────────────────────────────────────

    /**
     * key=value  (log-line style)
     * Matches e.g. sessionToken=abc123  or  access_token = xyz
     */
    private val keyEqualsValueRegex: Regex = Regex(
        """\b($sensitiveKeysPattern)\s*=\s*\S+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * key: value  (header style, compact or with space)
     * Matches e.g. X-LC-Session: abc123  or  X-LC-Session:abc123
     */
    private val keyColonValueRegex: Regex = Regex(
        """\b($sensitiveKeysPattern)\s*:\s*\S+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * JSON-style "key":"value" or "key": "value"
     * Matches e.g. "sessionToken":"r_abc123"
     */
    private val jsonKeyValueRegex: Regex = Regex(
        """"($sensitiveKeysPattern)"\s*:\s*"[^"]*"""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Authorization: Bearer/MAC <token-or-params>
     * Consumes the entire remaining line so MAC parameters (nonce, mac, etc)
     * are not leaked.
     */
    private val authHeaderRegex: Regex = Regex(
        """\b(Authorization)\s*:\s*(Bearer|MAC)\b.*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Standalone Bearer token (not preceded by Authorization:).
     * Matches RFC 6750 / RFC 6749 token characters.
     * Requires at least one base64url character after "Bearer ".
     */
    private val standaloneBearerRegex: Regex = Regex(
        """(?<!\w)(Bearer)\s+([A-Za-z0-9\-._~+/]+=*)""",
        RegexOption.IGNORE_CASE,
    )

    // ── Public API ────────────────────────────────────────────────

    /**
     * Redact sensitive key-value, header, and token patterns from [text].
     *
     * Order of operations:
     * 1. Authorization: Bearer/MAC (specific; preserves scheme)
     * 2. Standalone Bearer tokens
     * 3. JSON-style "key":"value"
     * 4. key: value (header style)
     * 5. key=value (log style)
     *
     * Returns the redacted string.
     */
    fun redact(text: String): String {
        var result = text

        // 1. Authorization: Bearer/MAC — preserve scheme, redact token
        result = authHeaderRegex.replace(result) { match ->
            "${match.groupValues[1]}: ${match.groupValues[2]} <redacted>"
        }

        // 2. Standalone Bearer tokens
        result = standaloneBearerRegex.replace(result) { match ->
            "${match.groupValues[1]} <redacted>"
        }

        // 3. JSON-style "key":"value"
        result = jsonKeyValueRegex.replace(result) { match ->
            val key = match.groupValues[1]
            "\"$key\":\"<redacted>\""
        }

        // 4. key: value (header style)
        result = keyColonValueRegex.replace(result) { match ->
            "${match.groupValues[1]}: <redacted>"
        }

        // 5. key=value (log style)
        result = keyEqualsValueRegex.replace(result) { match ->
            "${match.groupValues[1]}=<redacted>"
        }

        return result
    }
}
