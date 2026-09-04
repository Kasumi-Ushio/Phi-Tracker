package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.B30TagAnalysis
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.CategoryScore
import org.kasumi321.ushio.phitracker.domain.model.TagScore

/**
 * B30 chart-tag cluster analysis (pure local computation, ported from
 * phi-plugin commit e881a270's buildB30TagAnalysis: per-chart tag weight =
 * tag votes / that chart's highest tag votes, contribution = equivalent
 * single-chart RKS x weight, aggregated per tag and per top-level category).
 */
class AnalyzeB30TagsUseCase {
    operator fun invoke(
        records: List<BestRecord>,
        batch: B30ChartTagBatch,
        minimumVotes: Int = MIN_B30_TAG_VOTES
    ): B30TagAnalysis {
        val tagTotals = mutableMapOf<String, TagAccumulator>()
        var totalVotes = 0

        for (record in records) {
            val chartTags = batch.votesFor(record.songId, record.difficulty)
            val maxVotes = chartTags.values.maxOrNull() ?: 0
            if (maxVotes <= 0) continue
            for ((name, rawVotes) in chartTags) {
                if (rawVotes <= 0) continue
                val weight = rawVotes.toFloat() / maxVotes
                val acc = tagTotals.getOrPut(name) { TagAccumulator() }
                acc.weightedRks += record.rks * weight
                acc.weight += weight
                acc.votes += rawVotes
                totalVotes += rawVotes
            }
        }

        val tags = tagTotals.map { (name, acc) ->
            TagScore(
                name = name,
                rks = if (acc.weight > 0f) acc.weightedRks / acc.weight else 0f,
                votes = acc.votes
            )
        }.sortedWith(compareByDescending<TagScore> { it.rks }.thenByDescending { it.votes }.thenBy { it.name })

        val strong = tags.take(STRONG_WEAK_COUNT)
        val strongNames = strong.map { it.name }.toSet()
        val weak = tags.asReversed().filter { it.name !in strongNames }.take(STRONG_WEAK_COUNT)

        val averageRks = if (records.isNotEmpty()) {
            records.sumOf { it.rks.toDouble() }.toFloat() / records.size
        } else 0f

        return B30TagAnalysis(
            totalVotes = totalVotes,
            averageRks = averageRks,
            categories = buildCategorySummary(records, batch, averageRks),
            strong = strong,
            weak = weak,
            insufficient = totalVotes < minimumVotes || strong.size < STRONG_WEAK_COUNT || weak.size < STRONG_WEAK_COUNT
        )
    }

    private fun buildCategorySummary(
        records: List<BestRecord>,
        batch: B30ChartTagBatch,
        averageRks: Float
    ): List<CategoryScore> {
        val totals = mutableMapOf<String, CategoryAccumulator>()

        for (record in records) {
            val categoryNodes = batch.categoriesFor(record.songId, record.difficulty)
            val chartTags = batch.votesFor(record.songId, record.difficulty)
            val maxVotes = chartTags.values.maxOrNull() ?: 0
            if (maxVotes <= 0) continue
            categoryNodes.forEachIndexed { index, node ->
                if (node.voteCount <= 0) return@forEachIndexed
                val acc = totals.getOrPut(node.name) {
                    CategoryAccumulator(node.name, order = node.sortOrder.takeIf { it != 0 } ?: index)
                }
                val weight = node.voteCount.toFloat() / maxVotes
                acc.weightedRks += record.rks * weight
                acc.weightSum += weight
            }
        }

        return totals.values
            .sortedWith(compareBy { it.order })
            .map { acc ->
                CategoryScore(
                    name = acc.name,
                    rks = if (acc.weightSum > 0f) acc.weightedRks / acc.weightSum else averageRks,
                    votes = acc.weightSum,
                    hasVotes = acc.weightSum > 0f
                )
            }
    }

    private class TagAccumulator {
        var weightedRks = 0f
        var weight = 0f
        var votes = 0
    }

    private class CategoryAccumulator(val name: String, val order: Int) {
        var weightedRks = 0f
        var weightSum = 0f
    }

    companion object {
        const val MIN_B30_TAG_VOTES = 30
        private const val STRONG_WEAK_COUNT = 3
    }
}
