package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.ChartTagCategoryDisplay
import org.kasumi321.ushio.phitracker.domain.model.ChartTagVoteCount
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository

/** phi-plugin-api identity: platform triplet plus an optional api_token. */
data class ChartTagApiIdentity(
    val platform: String,
    val platformId: String,
    val apiUserId: String,
    val apiToken: String = ""
) {
    val isComplete: Boolean
        get() = platform.isNotBlank() && platformId.isNotBlank() && apiUserId.isNotBlank()
}

/**
 * Loads the community chart tags for one chart: cached category tree +
 * per-chart vote counts + the current user's votes, merged into category
 * display models. A usersVote failure must not block display (it only loses
 * the "voted by me" markers).
 *
 * [ChartTagsResult.display] keeps only tags with votes for the tag card;
 * [ChartTagsResult.all] keeps the full tree skeleton for the vote picker.
 */
class GetChartTagsUseCase(
    private val repository: PhigrosRepository
) {
    suspend operator fun invoke(
        songId: String,
        difficulty: Difficulty,
        identity: ChartTagApiIdentity?
    ): Result<ChartTagsResult> = runCatching {
        val tree = repository.getChartTagTree().getOrThrow()
        val data = repository.getChartTags(songId, difficulty).getOrThrow()

        val mine = identity
            ?.takeIf { it.isComplete }
            ?.let {
                repository.getMyChartTagVotes(
                    songId, difficulty, it.platform, it.platformId, it.apiUserId,
                    it.apiToken.takeIf { token -> token.isNotBlank() }
                ).getOrDefault(emptySet())
            }
            .orEmpty()

        val all = tree.map { node ->
            ChartTagCategoryDisplay(
                name = node.name,
                description = node.description,
                sortOrder = node.sortOrder,
                tags = node.children
                    .map { child ->
                        ChartTagVoteCount(
                            name = child.name,
                            votes = data.tags[child.name] ?: 0,
                            primaryVotes = data.primary[child.name] ?: 0,
                            secondaryVotes = data.secondary[child.name] ?: 0,
                            isMine = child.name in mine
                        )
                    }
                    .sortedWith(compareByDescending<ChartTagVoteCount> { it.votes }.thenBy { it.name })
            )
        }
        ChartTagsResult(
            display = all.map { category ->
                category.copy(tags = category.tags.filter { it.votes > 0 })
            },
            all = all
        )
    }
}

data class ChartTagsResult(
    val display: List<ChartTagCategoryDisplay>,
    val all: List<ChartTagCategoryDisplay>
)
