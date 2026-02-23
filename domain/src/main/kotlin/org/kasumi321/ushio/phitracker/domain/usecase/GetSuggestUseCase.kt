package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import javax.inject.Inject

/**
 * 推分建议
 */
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

class GetSuggestUseCase @Inject constructor() {

    /**
     * 计算所有可推分的曲目建议
     *
     * @param currentB30 当前 B30 列表
     * @param records 所有成绩
     * @param difficulties 定数表
     * @param songNames 歌名表
     * @param limit 返回前 N 条建议
     */
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

            for ((difficulty, cc) in songDiffs) {
                val currentLevel = songRecord?.levels?.get(difficulty)
                val currentAcc = currentLevel?.accuracy
                val currentRks = if (currentAcc != null) {
                    RksCalculator.calculateSingleRks(currentAcc, cc)
                } else 0f

                // 跳过已经高于 B19 末位的
                if (currentRks >= b19LastRks) continue

                val targetAcc = RksCalculator.calculateTargetAcc(b19LastRks, cc) ?: continue

                // 只推荐比当前 ACC 高 0.01%-15% 的目标
                if (currentAcc != null && (targetAcc - currentAcc) > 15f) continue

                val potentialRks = RksCalculator.calculateSingleRks(targetAcc, cc)

                suggestions.add(
                    SuggestItem(
                        songId = songId,
                        songName = songName,
                        difficulty = difficulty,
                        chartConstant = cc,
                        currentAcc = currentAcc,
                        targetAcc = targetAcc,
                        currentRks = currentRks,
                        potentialRks = potentialRks
                    )
                )
            }
        }

        return suggestions
            .sortedBy { it.targetAcc - (it.currentAcc ?: 0f) }
            .take(limit)
    }
}
