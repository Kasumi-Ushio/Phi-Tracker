package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

/**
 * Submits a chart-tag vote. The voting endpoint always requires an
 * api_token: fail fast in the use case before any request is sent.
 */
class VoteChartTagsUseCase(
    private val repository: PhigrosRepository
) {
    suspend operator fun invoke(
        songId: String,
        difficulty: Difficulty,
        primaryTags: List<String>,
        secondaryTags: List<String>,
        identity: ChartTagApiIdentity
    ): Result<Unit> {
        if (identity.apiToken.isBlank()) {
            return Result.failure(IllegalStateException("缺少 API Token"))
        }
        if (!identity.isComplete) {
            return Result.failure(IllegalStateException("API 身份未配置完整"))
        }
        if (primaryTags.isEmpty() && secondaryTags.isEmpty()) {
            return Result.failure(IllegalStateException("请至少选择一个标签"))
        }
        return repository.voteChartTags(
            songId = songId,
            difficulty = difficulty,
            primaryTags = primaryTags,
            secondaryTags = secondaryTags,
            platform = identity.platform,
            platformId = identity.platformId,
            apiUserId = identity.apiUserId,
            apiToken = identity.apiToken
        )
    }
}
