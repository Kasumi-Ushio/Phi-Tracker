package org.kasumi321.ushio.phitracker.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

/**
 * Port coverage of phi-plugin's buildB30TagAnalysis (commit e881a270):
 * per-chart tag weight = tag votes / that chart's highest tag votes, so a
 * tag's equivalent RKS is the weighted average of the charts carrying it.
 */
class AnalyzeB30TagsUseCaseTest {

    private val useCase = AnalyzeB30TagsUseCase()

    @Test
    fun singleChartTagEqualsChartRksAndStaysInsufficientBelowMinimum() {
        val records = listOf(record("song-a.0", rks = 15f))
        val batch = batch(
            mapOf("song-a.0" to mapOf("高速" to 29)),
            mapOf("song-a.0" to listOf(category("配置", voteCount = 29)))
        )

        val analysis = useCase(records, batch)

        assertEquals(29, analysis.totalVotes)
        assertEquals(15f, analysis.averageRks, absoluteTolerance = 1e-5f)
        assertEquals(listOf("高速"), analysis.strong.map { it.name })
        assertEquals(15f, analysis.strong.single().rks, absoluteTolerance = 1e-5f)
        assertTrue(analysis.insufficient, "fewer than 3 strong/weak tags must mark the analysis insufficient")
        assertEquals(listOf("配置"), analysis.categories.map { it.name })
        assertEquals(15f, analysis.categories.single().rks, absoluteTolerance = 1e-5f)
        assertTrue(analysis.categories.single().hasVotes)
    }

    @Test
    fun weightsNormalizeAgainstEachChartsMaxVotes() {
        // Chart A: 高速 40 (weight 1.0, rks 16), 连打 20 (weight 0.5)
        // Chart B: 连打 30 (weight 1.0, rks 14), 高速 15 (weight 0.5)
        val records = listOf(record("song-a.0", rks = 16f), record("song-b.0", rks = 14f))
        val batch = batch(
            mapOf(
                "song-a.0" to mapOf("高速" to 40, "连打" to 20),
                "song-b.0" to mapOf("连打" to 30, "高速" to 15)
            ),
            emptyMap()
        )

        val analysis = useCase(records, batch)

        // 高速: (16*1.0 + 14*0.5) / (1.0 + 0.5) = 23/1.5; 连打: (16*0.5 + 14*1.0) / 1.5
        assertEquals(23f / 1.5f, analysis.strong.first { it.name == "高速" }.rks, absoluteTolerance = 1e-4f)
        assertEquals(22f / 1.5f, analysis.strong.first { it.name == "连打" }.rks, absoluteTolerance = 1e-4f)
        assertEquals(listOf("高速", "连打"), analysis.strong.map { it.name })
        assertEquals(105, analysis.totalVotes)
        assertEquals(15f, analysis.averageRks, absoluteTolerance = 1e-5f)
    }

    @Test
    fun strongAndWeakRankingsAreDisjointAndCappedAtThree() {
        val votes = mapOf("高速" to 60, "连打" to 50, "多指" to 40, "散打" to 30, "停顿" to 20, "爆发" to 10)
        val records = listOf(record("song-a.0", rks = 15f))
        val batch = batch(
            mapOf("song-a.0" to votes),
            mapOf("song-a.0" to listOf(category("配置", voteCount = 110), category("手法", voteCount = 100)))
        )

        val analysis = useCase(records, batch)

        assertFalse(analysis.insufficient)
        assertEquals(listOf("高速", "连打", "多指"), analysis.strong.map { it.name })
        assertEquals(listOf("爆发", "停顿", "散打"), analysis.weak.map { it.name })
        assertTrue((analysis.strong.map { it.name }.toSet() intersect analysis.weak.map { it.name }.toSet()).isEmpty())
        // Categories keep their sortOrder and fall back to average Rks
        // only when a category has no weighted votes.
        assertEquals(listOf("配置", "手法"), analysis.categories.map { it.name })
    }

    @Test
    fun chartsWithoutVotesContributeNothing() {
        val records = listOf(record("song-a.0", rks = 16f), record("song-b.0", rks = 14f))
        val batch = batch(
            mapOf("song-a.0" to mapOf("高速" to 40), "song-b.0" to emptyMap()),
            emptyMap()
        )

        val analysis = useCase(records, batch)

        assertEquals(40, analysis.totalVotes)
        assertEquals(listOf("高速"), analysis.strong.map { it.name })
        assertEquals(16f, analysis.strong.single().rks, absoluteTolerance = 1e-5f)
        assertTrue(analysis.categories.isEmpty())
    }

    private fun record(songId: String, rks: Float) = BestRecord(
        songId = songId,
        songName = songId,
        difficulty = Difficulty.IN,
        score = 1_000_000,
        accuracy = 100f,
        isFullCombo = true,
        chartConstant = 15f,
        rks = rks,
        isPhi = true
    )

    private fun category(name: String, voteCount: Int, sortOrder: Int = 0) =
        ChartTagTreeNode(id = 0L, name = name, description = null, sortOrder = sortOrder, voteCount = voteCount)

    private fun batch(
        tags: Map<String, Map<String, Int>>,
        categories: Map<String, List<ChartTagTreeNode>>
    ): B30ChartTagBatch {
        fun <T> Map<String, T>.byDifficulty() = mapValues { (_, value) -> mapOf(Difficulty.IN to value) }
        return B30ChartTagBatch(tags = tags.byDifficulty(), categories = categories.byDifficulty())
    }
}
