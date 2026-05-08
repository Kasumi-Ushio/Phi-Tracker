package org.kasumi321.ushio.phitracker.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

class GetB30UseCase(
    private val repository: PhigrosRepository
) {
    operator fun invoke(
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>
    ): Flow<Pair<List<BestRecord>, List<BestRecord>>> {
        return repository.getCachedSave().map { save ->
            if (save == null) return@map Pair(emptyList(), emptyList())
            RksCalculator.getB30AndAllRecords(save.gameRecord, difficulties, songNames)
        }
    }
}
