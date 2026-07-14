package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.SongRecord

enum class SuggestTargetMode {
    Default,
    SingleChartRks,
    PlayerDisplayRks
}

data class SuggestItem(
    val songId: String,
    val songName: String,
    val difficulty: Difficulty,
    val chartConstant: Float,
    val currentScore: Int?,
    val currentAcc: Float?,
    val isFullCombo: Boolean,
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
        targetMode: SuggestTargetMode = SuggestTargetMode.Default,
        targetRks: Float? = null,
        limit: Int = 30
    ): List<SuggestItem> {
        if (targetMode != SuggestTargetMode.Default && targetRks != null) {
            return when (targetMode) {
                SuggestTargetMode.SingleChartRks -> buildSingleChartSuggestions(
                    targetRks = targetRks,
                    records = records,
                    difficulties = difficulties,
                    songNames = songNames,
                    limit = limit
                )
                SuggestTargetMode.PlayerDisplayRks -> buildPlayerTargetSuggestions(
                    targetDisplayRks = targetRks,
                    records = records,
                    difficulties = difficulties,
                    songNames = songNames
                )
                SuggestTargetMode.Default -> emptyList()
            }
        }
        return buildDefaultSuggestions(currentB30, records, difficulties, songNames, limit)
    }

    private fun buildDefaultSuggestions(
        currentB30: List<BestRecord>,
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>,
        limit: Int
    ): List<SuggestItem> {
        if (currentB30.size < 20) return emptyList()

        val b19LastRks = currentB30.getOrNull(19)?.rks ?: return emptyList()
        return buildDirectTargetSuggestions(
            targetRks = b19LastRks,
            records = records,
            difficulties = difficulties,
            songNames = songNames,
            limit = limit
        )
    }

    private fun buildSingleChartSuggestions(
        targetRks: Float,
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>,
        limit: Int
    ): List<SuggestItem> {
        if (targetRks !in 0f..17f) return emptyList()
        return buildDirectTargetSuggestions(
            targetRks = targetRks,
            records = records,
            difficulties = difficulties,
            songNames = songNames,
            limit = limit,
            enforceAchievableAccWindow = false
        )
    }

    private fun buildDirectTargetSuggestions(
        targetRks: Float,
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>,
        limit: Int,
        // The no-input Default view keeps the "within +15% accuracy" achievability
        // window so it stays a short, realistic shortlist; the user-driven target
        // modes disable it to surface every qualifying chart.
        enforceAchievableAccWindow: Boolean = true
    ): List<SuggestItem> {
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
                if (currentRks >= targetRks) continue
                val targetAcc = RksCalculator.calculateTargetAcc(targetRks, chartConstant) ?: continue
                if (enforceAchievableAccWindow && currentAcc != null && (targetAcc - currentAcc) > 15f) continue
                val potentialRks = RksCalculator.calculateSingleRks(targetAcc, chartConstant)
                suggestions.add(
                    SuggestItem(
                        songId = songId,
                        songName = songName,
                        difficulty = difficulty,
                        chartConstant = chartConstant,
                        currentScore = currentLevel?.score,
                        currentAcc = currentAcc,
                        isFullCombo = currentLevel?.isFullCombo == true,
                        targetAcc = targetAcc,
                        currentRks = currentRks,
                        potentialRks = potentialRks
                    )
                )
            }
        }

        return suggestions.sortedBy { it.targetAcc - (it.currentAcc ?: 0f) }.take(limit)
    }

    private fun buildPlayerTargetSuggestions(
        targetDisplayRks: Float,
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>
    ): List<SuggestItem> {
        if (targetDisplayRks !in 0f..17f) return emptyList()

        val currentRecords = flattenCurrentRecords(records, difficulties, songNames)
        val currentContribution = calculateContribution(currentRecords)
        val targetContribution = targetDisplayRks * 30f
        if (currentContribution >= targetContribution) return emptyList()

        val suggestions = mutableListOf<SuggestItem>()
        for ((songId, songDiffs) in difficulties) {
            val songRecord = records[songId]
            val songName = songNames[songId] ?: songId
            for ((difficulty, chartConstant) in songDiffs) {
                val currentLevel = songRecord?.levels?.get(difficulty)
                val currentAcc = currentLevel?.accuracy
                val currentRks = if (currentAcc != null) {
                    RksCalculator.calculateSingleRks(currentAcc, chartConstant)
                } else {
                    0f
                }

                val maxContribution = contributionWithCandidate(
                    baseRecords = currentRecords,
                    candidate = CandidateRecord(
                        songId = songId,
                        songName = songName,
                        difficulty = difficulty,
                        chartConstant = chartConstant,
                        currentLevel = currentLevel
                    ),
                    candidateRks = chartConstant
                )
                // Surface every chart that can *help* reach the target, not only
                // charts able to close the whole gap single-handedly. Because the
                // displayed RKS averages 30 charts, one chart alone rarely covers a
                // meaningful gap, so the old `< targetContribution` gate left the list
                // nearly empty. A chart helps whenever pushing it to its ceiling raises
                // the player's B30 contribution at all; charts that cannot reach the
                // target on their own fall through to the `chartConstant` (AP) goal
                // below and are kept only when that goal is realistically achievable.
                if (maxContribution <= currentContribution) continue

                var low = currentRks.coerceIn(0f, chartConstant)
                var high = chartConstant
                repeat(32) {
                    val mid = (low + high) / 2f
                    val contribution = contributionWithCandidate(
                        baseRecords = currentRecords,
                        candidate = CandidateRecord(songId, songName, difficulty, chartConstant, currentLevel),
                        candidateRks = mid
                    )
                    if (contribution >= targetContribution) {
                        high = mid
                    } else {
                        low = mid
                    }
                }

                var neededRks = high.coerceIn(currentRks, chartConstant)
                val reached = contributionWithCandidate(
                    baseRecords = currentRecords,
                    candidate = CandidateRecord(songId, songName, difficulty, chartConstant, currentLevel),
                    candidateRks = neededRks
                ) >= targetContribution
                if (!reached) neededRks = chartConstant

                val targetAcc = RksCalculator.calculateTargetAcc(neededRks, chartConstant) ?: continue
                if (currentAcc != null && targetAcc <= currentAcc) continue

                suggestions.add(
                    SuggestItem(
                        songId = songId,
                        songName = songName,
                        difficulty = difficulty,
                        chartConstant = chartConstant,
                        currentScore = currentLevel?.score,
                        currentAcc = currentAcc,
                        isFullCombo = currentLevel?.isFullCombo == true,
                        targetAcc = targetAcc,
                        currentRks = currentRks,
                        potentialRks = RksCalculator.calculateSingleRks(targetAcc, chartConstant)
                    )
                )
            }
        }

        return suggestions
            .sortedWith(compareBy<SuggestItem> { it.targetAcc - (it.currentAcc ?: 0f) }.thenByDescending { it.potentialRks })
    }

    private data class ContributionRecord(
        val songId: String,
        val songName: String,
        val difficulty: Difficulty,
        val score: Int,
        val accuracy: Float,
        val isFullCombo: Boolean,
        val chartConstant: Float,
        val rks: Float
    )

    private data class CandidateRecord(
        val songId: String,
        val songName: String,
        val difficulty: Difficulty,
        val chartConstant: Float,
        val currentLevel: LevelRecord?
    )

    private fun flattenCurrentRecords(
        records: Map<String, SongRecord>,
        difficulties: Map<String, Map<Difficulty, Float>>,
        songNames: Map<String, String>
    ): List<ContributionRecord> {
        return records.values.flatMap { songRecord ->
            val songDiffs = difficulties[songRecord.songId].orEmpty()
            songRecord.levels.mapNotNull { (difficulty, level) ->
                if (level == null) return@mapNotNull null
                val chartConstant = songDiffs[difficulty] ?: return@mapNotNull null
                ContributionRecord(
                    songId = songRecord.songId,
                    songName = songNames[songRecord.songId] ?: songRecord.songId,
                    difficulty = difficulty,
                    score = level.score,
                    accuracy = level.accuracy,
                    isFullCombo = level.isFullCombo,
                    chartConstant = chartConstant,
                    rks = RksCalculator.calculateSingleRks(level.accuracy, chartConstant)
                )
            }
        }
    }

    private fun contributionWithCandidate(
        baseRecords: List<ContributionRecord>,
        candidate: CandidateRecord,
        candidateRks: Float
    ): Float {
        val keyMatches: (ContributionRecord) -> Boolean = {
            it.songId == candidate.songId && it.difficulty == candidate.difficulty
        }
        val withoutCandidate = baseRecords.filterNot(keyMatches)
        val candidateRecord = ContributionRecord(
            songId = candidate.songId,
            songName = candidate.songName,
            difficulty = candidate.difficulty,
            score = candidate.currentLevel?.score ?: 0,
            accuracy = if (candidateRks >= candidate.chartConstant - 0.0001f) 100f else 0f,
            isFullCombo = candidate.currentLevel?.isFullCombo == true,
            chartConstant = candidate.chartConstant,
            rks = candidateRks
        )
        return calculateContribution(withoutCandidate + candidateRecord)
    }

    private fun calculateContribution(records: List<ContributionRecord>): Float {
        val phiSum = records
            .filter { it.accuracy >= 100f }
            .sortedByDescending { it.rks }
            .take(3)
            .sumOf { it.rks.toDouble() }
        val b27Sum = records
            .sortedByDescending { it.rks }
            .take(27)
            .sumOf { it.rks.toDouble() }

        // Phi3 and B27 are independent contribution slots; a Phi chart can count in both.
        return (phiSum + b27Sum).toFloat()
    }
}
