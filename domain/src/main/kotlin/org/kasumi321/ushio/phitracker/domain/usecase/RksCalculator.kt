package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.SongRecord

/**
 * RKS 计算引擎
 *
 * 基于 phi-plugin 的 fCompute.js 实现
 * B30 结构: Phi(3) + B27, 总 RKS = sum / 30
 */
object RksCalculator {

    /**
     * 计算单曲 RKS (等效 rks)
     *
     * - acc >= 100: 直接返回定数
     * - acc < 70: RKS = 0
     * - 70 <= acc < 100: ((acc - 55) / 45)^2 * chartConstant
     */
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

    /**
     * 计算显示 RKS
     *
     * Phi(3) + B27 / 30 结构:
     * - Phi: AP (acc >= 100) 成绩中 RKS 最高的 3 首
     * - B27: 所有成绩中 RKS 排名最高的 27 首
     * - 显示 RKS = (Phi3 之和 + B27 之和) / 30
     *
     * 注意: Phi 和 B27 可以重叠, 重叠部分在两边各算一次
     */
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

    /**
     * 从成绩记录中提取 B30 (Phi3 + B27) 列表
     *
     * 返回格式: Phi3 在前 (isPhi=true), B27 在后 (isPhi=false), 各自按 RKS 降序
     * Phi 和 B27 允许重叠 (同一首歌可同时出现在两个列表中)
     */
    fun getB30(
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>
    ): List<BestRecord> {
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

        // Phi3: AP 成绩 (acc >= 100) 按 RKS 降序, 取前 3
        val phi3 = allRecords
            .filter { it.accuracy >= 100f }
            .sortedByDescending { it.rks }
            .take(3)
            .map { it.copy(isPhi = true) }

        // B36 (27 B27 + 9 Overflow): 所有成绩按 RKS 降序, 取前 36
        val b36 = allRecords
            .sortedByDescending { it.rks }
            .take(36)

        // 合并: Phi3 在前, B36 在后
        return phi3 + b36
    }

    /**
     * 计算推分建议
     */
    fun calculateTargetAcc(targetRks: Float, chartConstant: Float): Float? {
        if (chartConstant <= 0f) return null
        val sqrtPart = Math.sqrt((targetRks / chartConstant).toDouble()).toFloat()
        val neededAcc = sqrtPart * 45f + 55f
        return if (neededAcc in 70f..100f) neededAcc else null
    }
}
