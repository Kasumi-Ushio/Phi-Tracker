package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongRecord

data class SuggestItem(
    val songId: String,
    val songName: String,
    val difficulty: Difficulty,
    val chartConstant: Float,
    val currentAcc: Float?,
    val targetAcc: Float,
    val currentRks: Float,
    val potentialRks: Float
)

class GetSuggestUseCase {
    operator fun invoke(
        currentB30: List<BestRecord>,
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>,
        limit: Int = 30
    ): List<SuggestItem> {
        if (currentB30.size < 20) return emptyList()

        val b19LastRks = currentB30.getOrNull(19)?.rks ?: return emptyList()
        val suggestions = mutableListOf<SuggestItem>()

        for ((songId, songDiffs) in difficulties) {
            val songName = songNames[songId] ?: songId
            val songRecord = records[songId]
            for ((difficulty, chartConstant) in songDiffs) {
                val currentLevel = songRecord?.levels?.get(difficulty)
                val currentAcc = currentLevel?.accuracy
                val currentRks = if (currentAcc != null) {
                    RksCalculator.calculateSingleRks(currentAcc, chartConstant)
                } else {
                    0f
                }
                if (currentRks >= b19LastRks) continue
                val targetAcc = RksCalculator.calculateTargetAcc(b19LastRks, chartConstant) ?: continue
                if (currentAcc != null && (targetAcc - currentAcc) > 15f) continue
                val potentialRks = RksCalculator.calculateSingleRks(targetAcc, chartConstant)
                suggestions.add(
                    SuggestItem(
                        songId = songId,
                        songName = songName,
                        difficulty = difficulty,
                        chartConstant = chartConstant,
                        currentAcc = currentAcc,
                        targetAcc = targetAcc,
                        currentRks = currentRks,
                        potentialRks = potentialRks
                    )
                )
            }
        }

        return suggestions.sortedBy { it.targetAcc - (it.currentAcc ?: 0f) }.take(limit)
    }
}
