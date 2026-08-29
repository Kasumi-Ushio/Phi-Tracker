package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo

class CheckForUpdateUseCase(private val repository: PhigrosRepository) {
    suspend operator fun invoke(
        currentVersionName: String,
        includePreRelease: Boolean
    ): Result<ReleaseInfo?> = repository.fetchLatestRelease(includePreRelease).map { release ->
        release.takeIf { isNewerVersion(it.tagName.removePrefix("v"), currentVersionName) }
    }

    internal fun isNewerVersion(newer: String, current: String): Boolean {
        val newerParts = newer.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        repeat(maxOf(newerParts.size, currentParts.size)) { index ->
            val comparison = newerParts.getOrElse(index) { 0 }
                .compareTo(currentParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return false
    }
}
