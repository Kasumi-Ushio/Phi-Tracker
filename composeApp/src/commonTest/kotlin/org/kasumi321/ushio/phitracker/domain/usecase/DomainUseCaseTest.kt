package org.kasumi321.ushio.phitracker.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.LevelRecord
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongInfo
import org.kasumi321.ushio.phitracker.domain.model.SongRecord
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.data.api.GitHubRelease
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
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
    fun searchWildcardAsteriskMatchesNonSpaceChars() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha-Beta", "C", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap()),
            "gamma.0" to SongInfo("gamma.0", "Alpha Gamma Delta", "C", "", emptyMap()),
            "delta.0" to SongInfo("delta.0", "AlphaBeta", "C", "", emptyMap())
        )

        // Alpha* -> regex Alpha\S+ (Alpha followed by >=1 non-whitespace)
        // "Alpha Gamma Delta" fails because space after Alpha is whitespace, \S+ requires non-whitespace
        val result = SearchSongUseCase()("Alpha*", songs)
        assertEquals(listOf("Alpha-Beta", "AlphaBeta"), result.map { it.name })
    }

    @Test
    fun searchWildcardQuestionMatchesSingleNonSpace() {
        val songs = mapOf(
            "a.0" to SongInfo("a.0", "AB", "C", "", emptyMap()),
            "b.0" to SongInfo("b.0", "AXB", "C", "", emptyMap()),
            "c.0" to SongInfo("c.0", "A B", "C", "", emptyMap())
        )

        val result = SearchSongUseCase()("A?B", songs)
        assertEquals(listOf("AXB"), result.map { it.name })
    }

    @Test
    fun searchWildcardSpaceMatchesWhitespace() {
        val songs = mapOf(
            "a.0" to SongInfo("a.0", "Alpha Beta", "C", "", emptyMap()),
            "b.0" to SongInfo("b.0", "AlphaBeta", "C", "", emptyMap()),
            "c.0" to SongInfo("c.0", "Alpha  Beta", "C", "", emptyMap())
        )

        // "Alpha Beta" has no wildcards (*/?), so it's a contains match.
        // Lowercase contains "alpha beta" matches "Alpha Beta" but not "Alpha  Beta" (double space).
        val result = SearchSongUseCase()("Alpha Beta", songs)
        assertEquals(listOf("Alpha Beta"), result.map { it.name })
    }

    @Test
    fun searchBlankQueryReturnsAllSongs() {
        val songs = mapOf(
            "a.0" to SongInfo("a.0", "A", "", "", emptyMap()),
            "b.0" to SongInfo("b.0", "B", "", "", emptyMap())
        )
        assertEquals(2, SearchSongUseCase()("", songs).size)
        assertEquals(2, SearchSongUseCase()("   ", songs).size)
    }

    @Test
    fun searchConsecutiveAsterisksReturnsAllSongsImmediately() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha", "C", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap()),
            "gamma.0" to SongInfo("gamma.0", "Gamma", "C", "", emptyMap())
        )
        assertEquals(3, SearchSongUseCase()("**", songs).size)
        assertEquals(3, SearchSongUseCase()("***", songs).size)
    }

    @Test
    fun searchConsecutiveAsterisksWithSpacesStillUsesRegex() {
        val songs = mapOf(
            "alpha.0" to SongInfo("alpha.0", "Alpha Beta", "C", "", emptyMap()),
            "beta.0" to SongInfo("beta.0", "Beta", "C", "", emptyMap())
        )
        val result = SearchSongUseCase()("* *", songs)
        assertEquals(listOf("Alpha Beta"), result.map { it.name })
    }

    @Test
    fun b57ReturnsUpTo57Records() {
        val records = (0 until 60).associate { i ->
            val songId = "song-$i"
            songId to SongRecord(
                songId = songId,
                levels = mapOf(Difficulty.IN to LevelRecord(1_000_000 - i, (99.9f - i * 0.1f).coerceAtLeast(70f), false))
            )
        }
        val difficulties = (0 until 60).associate { i ->
            "song-$i" to mapOf(Difficulty.IN to (15f - i * 0.1f).coerceAtLeast(1f))
        }
        val names = (0 until 60).associate { i -> "song-$i" to "Song $i" }

        val (b30, allRecords) = RksCalculator.getB30AndAllRecords(records, difficulties, names)
        assertEquals(60, allRecords.size)
        assertEquals(57, b30.size)
    }

    @Test
    fun suggestItemPopulatesIsFullCombo() {
        val b30 = mutableListOf(
            createBestRecord("best.0", Difficulty.IN, 15f, 100f, 15f),
            createBestRecord("best.1", Difficulty.HD, 14f, 99.5f, 14f)
        )
        repeat(18) { i ->
            b30.add(createBestRecord("pad-$i", Difficulty.IN, 10f, 92f, 12f))
        }

        val records = mapOf(
            "song.0" to SongRecord(
                songId = "song.0",
                levels = mapOf(Difficulty.IN to LevelRecord(950_000, 95f, true))
            ),
            "song.1" to SongRecord(
                songId = "song.1",
                levels = mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
            )
        )
        val difficulties = mapOf(
            "song.0" to mapOf(Difficulty.IN to 15f),
            "song.1" to mapOf(Difficulty.IN to 15f)
        )
        val names = mapOf("song.0" to "Song 0", "song.1" to "Song 1")

        val useCase = GetSuggestUseCase()
        val results = useCase(b30, records, difficulties, names)

        assertEquals(2, results.size)
        assertEquals(true, results.first { it.songId == "song.0" }.isFullCombo)
        assertEquals(false, results.first { it.songId == "song.1" }.isFullCombo)
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

    private fun createBestRecord(
        songId: String,
        difficulty: Difficulty,
        chartConstant: Float,
        accuracy: Float,
        rks: Float
    ): BestRecord = BestRecord(
        songId = songId,
        songName = songId,
        difficulty = difficulty,
        score = 1_000_000,
        accuracy = accuracy,
        isFullCombo = false,
        chartConstant = chartConstant,
        rks = rks
    )

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

        override fun clearTokenSync() = Unit

        override suspend fun apiTest(): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiBind(platform: String, platformId: String, token: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetSingleSave(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetSave(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetSaveInfo(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRank(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetAvgAcc(songId: String, difficulty: String, minRks: Float?, maxRks: Float?): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetAllAvgAcc(songIds: List<String>): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetApFcTotal(songId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetFittedDifficulty(songId: String, difficulty: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRksStats(): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetSaveHistory(platform: String, platformId: String, request: List<String>): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetScoreHistory(platform: String, platformId: String, songId: String?, difficulty: String?): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRankByUser(platform: String, platformId: String): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> =
            Result.failure(IllegalStateException("Not implemented in Phase B"))

        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<GitHubRelease> =
            Result.failure(IllegalStateException("Not implemented in Phase E"))
    }
}
