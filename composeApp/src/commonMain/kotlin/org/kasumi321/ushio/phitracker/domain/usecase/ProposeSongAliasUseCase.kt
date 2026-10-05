package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

/**
 * Submits a song-alias proposal to the community API. Authentication rides
 * on the login sessionToken (no api_token involved), so the only local
 * preconditions are a non-blank alias that the song does not already carry.
 * Proposals enter upstream review; nothing changes locally on success.
 */
class ProposeSongAliasUseCase(
    private val repository: PhigrosRepository
) {
    suspend operator fun invoke(
        songId: String,
        alias: String,
        note: String?,
        existingAliases: List<String>
    ): Result<Unit> {
        val trimmed = alias.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("别名不能为空"))
        }
        if (existingAliases.any { it.equals(trimmed, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("该别名已存在"))
        }
        return repository.proposeSongAlias(songId, trimmed, note?.trim()?.takeIf { it.isNotEmpty() })
    }
}
