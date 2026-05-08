package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

class SyncSaveUseCase(
    private val repository: PhigrosRepository
) {
    suspend operator fun invoke(sessionToken: String, server: Server): Result<Save> {
        return repository.syncSave(sessionToken, server)
    }
}
