package org.kasumi321.ushio.phitracker.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.settings.FakePhigrosRepository

class GetChartTagsUseCaseTest {

    @Test
    fun displayFiltersZeroVoteTagsWhileAllKeepsFullSkeleton() = runTest {
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(treeFixture())
            chartTagData = Result.success(dataFixture())
        }
        val useCase = GetChartTagsUseCase(repository)

        val result = useCase("song.0", Difficulty.IN, null).getOrThrow()

        // Display keeps only tags with votes; the untouched category still
        // appears (with an empty tag list) so the card structure is stable.
        assertEquals(listOf("高速", "连打"), result.display.single { it.name == "配置" }.tags.map { it.name })
        assertTrue(result.display.single { it.name == "手法" }.tags.isEmpty())
        // The picker skeleton keeps every leaf, including zero-vote ones.
        assertEquals(
            listOf("高速", "连打", "多指"),
            result.all.flatMap { category -> category.tags.map { it.name } }
        )
        assertEquals(0, result.all.single { it.name == "手法" }.tags.single().votes)
    }

    @Test
    fun voteCountsAndMineMarkersMergeIntoCategories() = runTest {
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(treeFixture())
            chartTagData = Result.success(dataFixture())
            myChartTagVotes = Result.success(setOf("连打"))
        }
        val useCase = GetChartTagsUseCase(repository)

        val result = useCase(
            "song.0",
            Difficulty.IN,
            ChartTagApiIdentity(platform = "taptap", platformId = "p", apiUserId = "u", apiToken = "t")
        ).getOrThrow()

        val voted = result.all.single { it.name == "配置" }.tags
        assertEquals(12, voted.first { it.name == "高速" }.votes)
        assertEquals(8, voted.first { it.name == "高速" }.primaryVotes)
        assertEquals(4, voted.first { it.name == "高速" }.secondaryVotes)
        assertFalse(voted.first { it.name == "高速" }.isMine)
        assertTrue(voted.first { it.name == "连打" }.isMine)
        assertEquals(listOf("taptap", "p", "u", "t"), repository.myChartTagVoteRequests.single().drop(2))
    }

    @Test
    fun usersVoteFailureDoesNotBlockDisplay() = runTest {
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(treeFixture())
            chartTagData = Result.success(dataFixture())
            myChartTagVotes = Result.failure(IllegalStateException("usersVote offline"))
        }
        val useCase = GetChartTagsUseCase(repository)

        val result = useCase(
            "song.0",
            Difficulty.IN,
            ChartTagApiIdentity(platform = "taptap", platformId = "p", apiUserId = "u")
        ).getOrThrow()

        assertEquals(listOf("高速", "连打"), result.display.single { it.name == "配置" }.tags.map { it.name })
        assertTrue(result.all.flatMap { it.tags }.none { it.isMine })
    }

    @Test
    fun incompleteIdentitySkipsUsersVoteRequest() = runTest {
        val repository = FakePhigrosRepository().apply {
            chartTagTree = Result.success(treeFixture())
            chartTagData = Result.success(dataFixture())
        }
        val useCase = GetChartTagsUseCase(repository)

        val result = useCase(
            "song.0",
            Difficulty.IN,
            ChartTagApiIdentity(platform = "taptap", platformId = "", apiUserId = "u")
        ).getOrThrow()

        assertTrue(repository.myChartTagVoteRequests.isEmpty())
        assertEquals(3, result.all.flatMap { it.tags }.size)
    }

    private fun treeFixture() = listOf(
        ChartTagTreeNode(
            id = 1L, name = "配置", description = null, sortOrder = 0,
            children = listOf(
                ChartTagTreeNode(id = 11L, name = "高速", description = null, sortOrder = 0),
                ChartTagTreeNode(id = 12L, name = "连打", description = null, sortOrder = 1)
            )
        ),
        ChartTagTreeNode(
            id = 2L, name = "手法", description = null, sortOrder = 1,
            children = listOf(
                ChartTagTreeNode(id = 21L, name = "多指", description = null, sortOrder = 0)
            )
        )
    )

    private fun dataFixture() = ChartTagSongData(
        songId = "song.0",
        difficulty = Difficulty.IN,
        tags = mapOf("高速" to 12, "连打" to 3),
        primary = mapOf("高速" to 8),
        secondary = mapOf("高速" to 4, "连打" to 3),
        categories = emptyList()
    )
}
