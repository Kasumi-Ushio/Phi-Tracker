package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.GameUpdateInfo
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

class FetchGameUpdateInfoUseCase(private val repository: PhigrosRepository) {
    suspend operator fun invoke(): Result<GameUpdateInfo> = repository.fetchGameUpdateInfo()
}
