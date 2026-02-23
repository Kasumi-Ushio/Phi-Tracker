package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import javax.inject.Inject

/**
 * 同步存档用例
 */
class SyncSaveUseCase @Inject constructor(
    private val repository: PhigrosRepository
) {
    suspend operator fun invoke(sessionToken: String, server: Server): Result<Save> {
        return repository.syncSave(sessionToken, server)
    }
}
