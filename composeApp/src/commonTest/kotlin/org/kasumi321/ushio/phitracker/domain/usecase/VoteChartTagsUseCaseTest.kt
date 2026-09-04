package org.kasumi321.ushio.phitracker.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.settings.FakePhigrosRepository

class VoteChartTagsUseCaseTest {

    @Test
    fun blankApiTokenFailsFastWithoutRepositoryCall() = runTest {
        val repository = FakePhigrosRepository()
        val useCase = VoteChartTagsUseCase(repository)

        val result = useCase(
            songId = "song.0",
            difficulty = Difficulty.IN,
            primaryTags = listOf("高速"),
            secondaryTags = emptyList(),
            identity = ChartTagApiIdentity(platform = "taptap", platformId = "p", apiUserId = "u", apiToken = " ")
        )

        assertTrue(result.isFailure)
        assertTrue(repository.voteChartTagRequests.isEmpty(), "no request may be sent without an api_token")
    }

    @Test
    fun incompleteIdentityFailsFastWithoutRepositoryCall() = runTest {
        val repository = FakePhigrosRepository()
        val useCase = VoteChartTagsUseCase(repository)

        val result = useCase(
            songId = "song.0",
            difficulty = Difficulty.IN,
            primaryTags = listOf("高速"),
            secondaryTags = emptyList(),
            identity = ChartTagApiIdentity(platform = "taptap", platformId = "", apiUserId = "u", apiToken = "token")
        )

        assertTrue(result.isFailure)
        assertTrue(repository.voteChartTagRequests.isEmpty())
    }

    @Test
    fun emptySelectionFailsFastWithoutRepositoryCall() = runTest {
        val repository = FakePhigrosRepository()
        val useCase = VoteChartTagsUseCase(repository)

        val result = useCase(
            songId = "song.0",
            difficulty = Difficulty.IN,
            primaryTags = emptyList(),
            secondaryTags = emptyList(),
            identity = ChartTagApiIdentity(platform = "taptap", platformId = "p", apiUserId = "u", apiToken = "token")
        )

        assertTrue(result.isFailure)
        assertTrue(repository.voteChartTagRequests.isEmpty())
    }

    @Test
    fun forwardsIdentityTagsAndTokenOnSuccess() = runTest {
        val repository = FakePhigrosRepository()
        val useCase = VoteChartTagsUseCase(repository)

        val result = useCase(
            songId = "song.0",
            difficulty = Difficulty.IN,
            primaryTags = listOf("高速", "连打"),
            secondaryTags = listOf("多指"),
            identity = ChartTagApiIdentity(platform = "taptap", platformId = "p", apiUserId = "u", apiToken = "token")
        )

        assertEquals(Result.success(Unit), result)
        val request = repository.voteChartTagRequests.single()
        assertEquals("song.0", request.songId)
        assertEquals(Difficulty.IN, request.difficulty)
        assertEquals(listOf("高速", "连打"), request.primaryTags)
        assertEquals(listOf("多指"), request.secondaryTags)
        assertEquals("taptap", request.platform)
        assertEquals("p", request.platformId)
        assertEquals("u", request.apiUserId)
        assertEquals("token", request.apiToken)
    }
}
