package org.kasumi321.ushio.phitracker.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class QrChallengeId(val value: String)

@JvmInline
value class QrAuthorizationId(val value: String)

data class QrLoginChallenge(
    val id: QrChallengeId,
    val qrUrl: String,
    val expiresAt: Long
)

sealed interface QrLoginPollResult {
    data object Pending : QrLoginPollResult
    data object AuthorizationWaiting : QrLoginPollResult
    data class Authorized(val authorizationId: QrAuthorizationId) : QrLoginPollResult
}
