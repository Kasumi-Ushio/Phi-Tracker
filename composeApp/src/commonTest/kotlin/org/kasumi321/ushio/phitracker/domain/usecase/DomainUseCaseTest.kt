package org.kasumi321.ushio.phitracker.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DomainUseCaseTest {
    @Test
    fun singleRksMatchesBeta1Thresholds() {
        assertEquals(0f, RksCalculator.calculateSingleRks(69.99f, 15f))
        assertEquals(15f, RksCalculator.calculateSingleRks(100f, 15f))

        val expected = ((95f - 55f) / 45f) * ((95f - 55f) / 45f) * 15f
        assertClose(expected, RksCalculator.calculateSingleRks(95f, 15f))
    }

    @Test
    fun b30ListKeepsPhiFirstThenTopRecords() {
        val records = mapOf(
            "song-a" to SongRecord(
                songId = "song-a",
                levels = mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true))
            ),
            "song-b" to SongRecord(
                songId = "song-b",
                levels = mapOf(Difficulty.IN to LevelRecord(990_000, 99f, true))
            ),
            "song-c" to SongRecord(
                songId = "song-c",
                levels = mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true))
            )
        )
        val difficulties = mapOf(
            "song-a" to mapOf(Difficulty.IN to 15f),
            "song-b" to mapOf(Difficulty.IN to 16f),
            "song-c" to mapOf(Difficulty.IN to 14f)
        )
        val names = mapOf("song-a" to "A", "song-b" to "B", "song-c" to "C")

        val (b30, allRecords) = RksCalculator.getB30AndAllRecords(records, difficulties, names)

        assertEquals(3, allRecords.size)
        assertEquals(listOf("song-a", "song-c"), b30.take(2).map { it.songId })
        assertTrue(b30.take(2).all { it.isPhi })
        assertTrue(b30.drop(2).all { !it.isPhi })
    }

    @Test
    fun searchMatchesNameIdAndComposerThenSortsByName() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha", "Composer Z", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "Alice", "", emptyMap()),
            "gamma.0" to SongInfo("gamma.0", "Gamma", "Composer Z", "", emptyMap())
        )

        assertEquals(listOf("Alpha", "Gamma"), SearchSongUseCase()("composer z", songs).map { it.name })
        assertEquals(listOf("Beta"), SearchSongUseCase()("beta.0", songs).map { it.name })
        assertEquals(3, SearchSongUseCase()("", songs).size)
    }

    @Test
    fun useCasesDelegateToRepository(): Unit = runTest {
        val save = emptySave()
        val repository = FakePhigrosRepository(save)

        assertEquals(save, SyncSaveUseCase(repository)("token", Server.CN).getOrThrow())
        assertEquals(Pair(emptyList(), emptyList()), GetB30UseCase(repository)(emptyMap(), emptyMap()).first())
        assertEquals(Pair("token", Server.CN), repository.lastSyncRequest)
    }

    private fun emptySave(): Save = Save(
        gameRecord = emptyMap(),
        gameProgress = GameProgress(
            isFirstRun = false,
            legacyChapterFinished = false,
            alreadyShowCollectionTip = false,
            alreadyShowAutoUnlockINTip = false,
            completed = "",
            songUpdateInfo = 0,
            challengeModeRank = 0,
            money = emptyList(),
            unlockFlagOfSpasmodic = 0,
            unlockFlagOfIgallta = 0,
            unlockFlagOfRrharil = 0,
            flagOfSongRecordKey = 0,
            randomVersionUnlocked = null,
            chapter8UnlockBegin = null,
            chapter8UnlockSecondPhase = null,
            chapter8Passed = null,
            chapter8SongUnlocked = null
        ),
        user = UserSettings(showPlayerId = false, selfIntro = "", avatar = "", background = ""),
        summary = null
    )

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.0001f, "Expected $expected but was $actual")
    }

    private class FakePhigrosRepository(
        private val save: Save
    ) : PhigrosRepository {
        var lastSyncRequest: Pair<String, Server>? = null

        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> {
            error("Not needed for this test")
        }

        override suspend fun syncSave(sessionToken: String, server: Server): Result<Save> {
            lastSyncRequest = Pair(sessionToken, server)
            return Result.success(save)
        }

        override fun getCachedSave(): Flow<Save?> = flowOf(save)

        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)

        override suspend fun saveSessionToken(token: String, server: Server) = Unit

        override suspend fun getSessionToken(): Pair<String, Server>? = null

        override suspend fun clearData() = Unit
    }
}
