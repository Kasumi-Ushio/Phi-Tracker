package org.kasumi321.ushio.phitracker.domain.repository

import org.kasumi321.ushio.phitracker.domain.model.QrAuthorizationId
import org.kasumi321.ushio.phitracker.domain.model.QrChallengeId
import org.kasumi321.ushio.phitracker.domain.model.QrLoginChallenge
import org.kasumi321.ushio.phitracker.domain.model.QrLoginPollResult
import org.kasumi321.ushio.phitracker.domain.model.Server

interface QrLoginRepository {
    suspend fun requestChallenge(server: Server): QrLoginChallenge
    suspend fun poll(challengeId: QrChallengeId): QrLoginPollResult
    suspend fun exchangeForSessionToken(authorizationId: QrAuthorizationId): String
}
