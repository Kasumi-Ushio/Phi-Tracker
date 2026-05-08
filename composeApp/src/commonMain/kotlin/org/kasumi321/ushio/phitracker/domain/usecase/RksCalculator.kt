package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import kotlin.math.sqrt

object RksCalculator {
    fun calculateSingleRks(accuracy: Float, chartConstant: Float): Float {
        return when {
            accuracy >= 100f -> chartConstant
            accuracy < 70f -> 0f
            else -> {
                val factor = (accuracy - 55f) / 45f
                factor * factor * chartConstant
            }
        }
    }

    fun calculateDisplayRks(allBest: List<BestRecord>): Float {
        if (allBest.isEmpty()) return 0f

        val phiRecords = allBest
            .filter { it.accuracy >= 100f }
            .sortedByDescending { it.rks }
            .take(3)

        val b27Records = allBest
            .sortedByDescending { it.rks }
            .take(27)

        val phiSum = phiRecords.sumOf { it.rks.toDouble() }
        val b27Sum = b27Records.sumOf { it.rks.toDouble() }

        return ((phiSum + b27Sum) / 30.0).toFloat()
    }

    fun getB30AndAllRecords(
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>
    ): Pair<List<BestRecord>, List<BestRecord>> {
        val allRecords = mutableListOf<BestRecord>()

        for ((songId, songRecord) in records) {
            val songDiffs = difficulties[songId] ?: continue
            val songName = songNames[songId] ?: songId

            for ((difficulty, levelRecord) in songRecord.levels) {
                if (levelRecord == null) continue
                val cc = songDiffs[difficulty] ?: continue
                val rks = calculateSingleRks(levelRecord.accuracy, cc)
                allRecords.add(
                    BestRecord(
                        songId = songId,
                        songName = songName,
                        difficulty = difficulty,
                        score = levelRecord.score,
                        accuracy = levelRecord.accuracy,
                        isFullCombo = levelRecord.isFullCombo,
                        chartConstant = cc,
                        rks = rks
                    )
                )
            }
        }

        val phi3 = allRecords
            .filter { it.accuracy >= 100f }
            .sortedByDescending { it.rks }
            .take(3)
            .map { it.copy(isPhi = true) }

        val b42 = allRecords
            .sortedByDescending { it.rks }
            .take(42)

        val b30List = phi3 + b42
        return Pair(b30List, allRecords)
    }

    fun calculateTargetAcc(targetRks: Float, chartConstant: Float): Float? {
        if (chartConstant <= 0f) return null
        val sqrtPart = sqrt((targetRks / chartConstant).toDouble()).toFloat()
        val neededAcc = sqrtPart * 45f + 55f
        return if (neededAcc in 70f..100f) neededAcc else null
    }
}
