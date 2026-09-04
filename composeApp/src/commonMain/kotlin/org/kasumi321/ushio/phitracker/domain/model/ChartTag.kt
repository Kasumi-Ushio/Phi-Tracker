package org.kasumi321.ushio.phitracker.domain.model

/**
 * A node of the chart-tag category tree (phi-plugin-api /chartsTag vocabulary).
 *
 * Nodes from the tree endpoint carry no vote counts; the same shape returned
 * by per-chart vote endpoints additionally carries voteCount /
 * primaryVoteCount / secondaryVoteCount.
 */
data class ChartTagTreeNode(
    val id: Long,
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val voteCount: Int = 0,
    val primaryVoteCount: Int = 0,
    val secondaryVoteCount: Int = 0,
    val children: List<ChartTagTreeNode> = emptyList()
)

/** Display item for a single leaf tag with its vote counts. */
data class ChartTagVoteCount(
    val name: String,
    val votes: Int,
    val primaryVotes: Int = 0,
    val secondaryVotes: Int = 0,
    val isMine: Boolean = false
)

/** Category-dimension display model (tree merged with per-chart votes). */
data class ChartTagCategoryDisplay(
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val tags: List<ChartTagVoteCount>
)

/** Tag data for a single chart (song + difficulty). */
data class ChartTagSongData(
    val songId: String,
    val difficulty: Difficulty,
    val tags: Map<String, Int>,
    val primary: Map<String, Int>,
    val secondary: Map<String, Int>,
    val categories: List<ChartTagTreeNode>
)

/** Parsed result of a B30 batch chart-tag response. */
data class B30ChartTagBatch(
    val tags: Map<String, Map<Difficulty, Map<String, Int>>>,
    val categories: Map<String, Map<Difficulty, List<ChartTagTreeNode>>>
) {
    fun votesFor(songId: String, difficulty: Difficulty): Map<String, Int> =
        tags[songId]?.get(difficulty) ?: emptyMap()

    fun categoriesFor(songId: String, difficulty: Difficulty): List<ChartTagTreeNode> =
        categories[songId]?.get(difficulty) ?: emptyList()
}

/** Weighted equivalent RKS for a single tag. */
data class TagScore(val name: String, val rks: Float, val votes: Int)

/** Weighted equivalent RKS for a single category; votes is the summed weight. */
data class CategoryScore(
    val name: String,
    val rks: Float,
    val votes: Float,
    val hasVotes: Boolean
)

/**
 * Result of the B30 chart-tag cluster analysis, shared by the B30 tab UI
 * and the export image (KMP-safe, no platform or UI types).
 */
data class B30TagAnalysis(
    val totalVotes: Int,
    val averageRks: Float,
    val categories: List<CategoryScore>,
    val strong: List<TagScore>,
    val weak: List<TagScore>,
    val insufficient: Boolean
)
