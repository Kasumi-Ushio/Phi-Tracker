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
        // Need 20 B30 records minimum for beta5 threshold (index 19)
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
        assertEquals(950_000, results.first { it.songId == "song.0" }.currentScore)
        assertEquals(false, results.first { it.songId == "song.1" }.isFullCombo)
    }

    @Test
    fun suggestSingleChartTargetUsesProvidedRks() {
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 8f)
        }
        val records = mapOf(
            "candidate" to SongRecord(
                songId = "candidate",
                levels = mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
            )
        )
        val difficulties = mapOf("candidate" to mapOf(Difficulty.IN to 16f))
        val names = mapOf("candidate" to "Candidate")

        val result = useCase(
            currentB30 = currentB30,
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.SingleChartRks,
            targetRks = 15f
        )

        assertEquals(1, result.size)
        assertClose(15f, result.single().potentialRks, tolerance = 0.001f)
        assertTrue(result.single().targetAcc > 98f)
    }

    @Test
    fun suggestPlayerTargetKeepsPhiAndB27AsSeparateContributionSlots() {
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 8f)
        }
        val records = mutableMapOf<String, SongRecord>()
        val difficulties = mutableMapOf<String, Map<Difficulty, Float>>()
        val names = mutableMapOf<String, String>()
        repeat(26) { i ->
            val songId = "base-$i"
            records[songId] = SongRecord(songId, mapOf(Difficulty.IN to LevelRecord(900_000, 95f, false)))
            difficulties[songId] = mapOf(Difficulty.IN to 10f)
            names[songId] = "Base $i"
        }
        records["candidate"] = SongRecord(
            "candidate",
            mapOf(Difficulty.IN to LevelRecord(900_000, 90f, false))
        )
        difficulties["candidate"] = mapOf(Difficulty.IN to 16f)
        names["candidate"] = "Candidate"

        val result = useCase(
            currentB30 = currentB30,
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.PlayerDisplayRks,
            targetRks = 7.8f
        )

        val candidate = result.single { it.songId == "candidate" }
        assertClose(16f, candidate.potentialRks, tolerance = 0.001f)
        assertClose(100f, candidate.targetAcc, tolerance = 0.001f)
    }

    @Test
    fun suggestPlayerTargetReturnsAllHelpfulCharts() {
        val useCase = GetSuggestUseCase()
        val currentB30 = (0 until 20).map { i ->
            BestRecord("b-$i", "B $i", Difficulty.IN, 900_000, 90f, false, 10f, 0f)
        }
        val difficulties = (0 until 40).associate { i ->
            "candidate-$i" to mapOf(Difficulty.IN to 17f)
        }
        val names = (0 until 40).associate { i -> "candidate-$i" to "Candidate $i" }

        val result = useCase(
            currentB30 = currentB30,
            records = emptyMap(),
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.PlayerDisplayRks,
            targetRks = 0.5f,
            limit = 30
        )

        assertEquals(40, result.size)
    }

    @Test
    fun suggestPlayerTargetIncludesChartsThatHelpButCannotSoloReachTarget() {
        val useCase = GetSuggestUseCase()
        // 30 AP'd charts at chart-constant 10 → displayed RKS 10, contribution 300.
        val records = mutableMapOf<String, SongRecord>()
        val difficulties = mutableMapOf<String, Map<Difficulty, Float>>()
        val names = mutableMapOf<String, String>()
        repeat(30) { i ->
            val id = "base-$i"
            records[id] = SongRecord(id, mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, true)))
            difficulties[id] = mapOf(Difficulty.IN to 10f)
            names[id] = "Base $i"
        }
        // Unplayed chart-constant 12 chart: AP'ing it lifts the contribution to 304 (a
        // real step toward the target) but cannot by itself reach 10.4 * 30 = 312. The
        // old "must close the whole gap alone" gate hid it; it must now be suggested.
        difficulties["candidate"] = mapOf(Difficulty.IN to 12f)
        names["candidate"] = "Candidate"

        val result = useCase(
            currentB30 = emptyList(),
            records = records,
            difficulties = difficulties,
            songNames = names,
            targetMode = SuggestTargetMode.PlayerDisplayRks,
            targetRks = 10.4f
        )

        val candidate = result.firstOrNull { it.songId == "candidate" }
            ?: error("A chart that helps but can't alone reach the target must be suggested")
        assertClose(100f, candidate.targetAcc, tolerance = 0.001f)
        assertTrue(
            result.none { it.songId.startsWith("base-") },
            "Already-maxed (AP) charts must not be suggested"
        )
    }

    @Test
    fun suggestBeta5ThresholdBehavior() {
        val useCase = GetSuggestUseCase()
        val diffs = mapOf("candidate" to mapOf(Difficulty.IN to 10f))
        val names = mapOf("candidate" to "Candidate")

        // 1. Less than 20 B30 records → empty
        val tiny = (0 until 19).map { i ->
            BestRecord("t-$i", "", Difficulty.IN, 0, 0f, false, 0f, 0f)
        }
        assertTrue(useCase(tiny, emptyMap(), diffs, names).isEmpty(),
            "<20 B30 records must return empty")

        // 2. Exactly 20 records: threshold = currentB30[19].rks
        val nonPhi = (0 until 20).map { i ->
            BestRecord("np-$i", "NP $i", Difficulty.IN, 1_000_000, 100f, false, 10f, 10f - i * 0.2f, isPhi = false)
        }
        val expectedThreshold = nonPhi[19].rks  // 10.0 - 19*0.2 = 6.2

        // Candidate below threshold (acc 80, cc 10: rks = ((80-55)/45)^2 * 10 = 3.09 < 6.2) → suggested
        val belowRecords = mapOf(
            "candidate" to SongRecord(
                "candidate", levels = mapOf(Difficulty.IN to LevelRecord(800_000, 80f, false))
            )
        )
        val below = useCase(nonPhi, belowRecords, diffs, names)
        assertTrue(below.isNotEmpty(), "Candidate below threshold should be suggested")

        // Candidate above threshold (acc 100, cc 10: rks = 10.0 >= 6.2) → NOT suggested
        val aboveRecords = mapOf(
            "candidate" to SongRecord(
                "candidate", levels = mapOf(Difficulty.IN to LevelRecord(1_000_000, 100f, false))
            )
        )
        val above = useCase(nonPhi, aboveRecords, diffs, names)
        assertTrue(above.isEmpty(), "Candidate above or at threshold should NOT be suggested")

        // 3. Phi record at index 19 sets the threshold (phi NOT filtered before threshold selection)
        val phiAt19 = nonPhi.toMutableList()
        phiAt19[19] = BestRecord("phi-19", "Phi 19", Difficulty.IN, 1_000_000, 100f, true, 10f, 8f, isPhi = true)
        // Now threshold = 8f. Candidate acc 94, cc 10: rks = ((94-55)/45)^2 * 10 = 7.51 < 8 → suggested
        val mediumRecords = mapOf(
            "candidate" to SongRecord(
                "candidate", levels = mapOf(Difficulty.IN to LevelRecord(940_000, 94f, false))
            )
        )
        // With original threshold 6.2: acc 94 rks=7.51 >= 6.2 → NOT suggested → empty
        assertTrue(useCase(nonPhi, mediumRecords, diffs, names).isEmpty(),
            "Without phi, threshold 6.2 excludes acc 94 candidate")
        // With phi-raised threshold 8f: acc 94 rks=7.51 < 8 → suggested
        assertTrue(useCase(phiAt19, mediumRecords, diffs, names).isNotEmpty(),
            "Phi at index 19 raises threshold to 8f, making acc 94 candidate suggestable")
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

    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.0001f) {
        assertTrue(abs(expected - actual) < tolerance, "Expected $expected but was $actual")
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
