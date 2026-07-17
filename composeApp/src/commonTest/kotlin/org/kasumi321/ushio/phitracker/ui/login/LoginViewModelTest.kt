package org.kasumi321.ushio.phitracker.ui.login

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.api.GitHubRelease
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repo: PhigrosRepository): LoginViewModel =
        LoginViewModel(repo, SyncSaveUseCase(repo), TapTapQrLoginApi(HttpClient(MockEngine { respond("") })))

    @Test
    fun noSavedTokenGoesToLogin() = runTest(dispatcher) {
        val vm = viewModel(FakeRepo(savedToken = null))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertFalse(vm.uiState.value.isLoggedIn)
    }

    @Test
    fun onlineValidateAndSyncSuccessLogsIn() = runTest(dispatcher) {
        val vm = viewModel(FakeRepo(savedToken = "t" to Server.CN, validateOk = true, syncOk = true))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertTrue(vm.uiState.value.isLoggedIn)
    }

    @Test
    fun offlineButCachedSavePresentStaysLoggedIn() = runTest(dispatcher) {
        // validate fails (offline) but a local save exists → must NOT log out.
        val vm = viewModel(FakeRepo(savedToken = "t" to Server.CN, validateOk = false, cachedSave = minimalSave()))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertTrue(vm.uiState.value.isLoggedIn, "offline with a cached save must stay logged in")
    }

    @Test
    fun syncFailsButCachedSavePresentStaysLoggedIn() = runTest(dispatcher) {
        // token still validates but the save sync fails; the cached save should keep us in.
        val vm = viewModel(FakeRepo(savedToken = "t" to Server.CN, validateOk = true, syncOk = false, cachedSave = minimalSave()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoggedIn)
    }

    @Test
    fun offlineWithoutCachedSaveFallsBackToLogin() = runTest(dispatcher) {
        val vm = viewModel(FakeRepo(savedToken = "t" to Server.CN, validateOk = false, cachedSave = null))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertFalse(vm.uiState.value.isLoggedIn, "no local save and no network → login")
        assertNotNull(vm.uiState.value.error)
    }

    // ── fakes ──────────────────────────────────────────────────────────

    private fun minimalSave(): Save = Save(
        gameRecord = emptyMap(),
        gameProgress = GameProgress(
            isFirstRun = false, legacyChapterFinished = false,
            alreadyShowCollectionTip = false, alreadyShowAutoUnlockINTip = false,
            completed = "", songUpdateInfo = 0, challengeModeRank = 0, money = emptyList(),
            unlockFlagOfSpasmodic = 0, unlockFlagOfIgallta = 0, unlockFlagOfRrharil = 0,
            flagOfSongRecordKey = 0, randomVersionUnlocked = null,
            chapter8UnlockBegin = null, chapter8UnlockSecondPhase = null,
            chapter8Passed = null, chapter8SongUnlocked = null
        ),
        user = UserSettings(showPlayerId = false, selfIntro = "", avatar = "", background = ""),
        summary = null
    )

    private inner class FakeRepo(
        private val savedToken: Pair<String, Server>?,
        private val validateOk: Boolean = false,
        private val syncOk: Boolean = false,
        private val cachedSave: Save? = null
    ) : PhigrosRepository {
        private fun offline() = Result.failure<Nothing>(RuntimeException("network unavailable"))

        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> =
            if (validateOk) {
                Result.success(UserProfile("id", "nick", "", "", "", 0f, 0, 0, ""))
            } else {
                Result.failure(RuntimeException("network unavailable"))
            }

        override suspend fun syncSave(sessionToken: String, server: Server): Result<Save> =
            if (syncOk) Result.success(minimalSave()) else Result.failure(RuntimeException("network unavailable"))

        override fun getCachedSave(): Flow<Save?> = flowOf(cachedSave)
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) = Unit
        override suspend fun getSessionToken(): Pair<String, Server>? = savedToken
        override suspend fun clearData() = Unit
        override fun clearTokenSync() = Unit

        override suspend fun apiTest(): Result<JsonObject> = offline()
        override suspend fun apiBind(platform: String, platformId: String, token: String): Result<JsonObject> = offline()
        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> = offline()
        override suspend fun apiGetSingleSave(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject> = offline()
        override suspend fun apiGetSave(platform: String, platformId: String): Result<JsonObject> = offline()
        override suspend fun apiGetSaveInfo(platform: String, platformId: String): Result<JsonObject> = offline()
        override suspend fun apiGetRank(platform: String, platformId: String, songId: String, difficulty: String): Result<JsonObject> = offline()
        override suspend fun apiGetAvgAcc(songId: String, difficulty: String, minRks: Float?, maxRks: Float?): Result<JsonObject> = offline()
        override suspend fun apiGetAllAvgAcc(songIds: List<String>): Result<JsonObject> = offline()
        override suspend fun apiGetApFcTotal(songId: String): Result<JsonObject> = offline()
        override suspend fun apiGetRksStats(): Result<JsonObject> = offline()
        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> = offline()
        override suspend fun apiGetSaveHistory(platform: String, platformId: String, request: List<String>): Result<JsonObject> = offline()
        override suspend fun apiGetScoreHistory(platform: String, platformId: String, songId: String?, difficulty: String?): Result<JsonObject> = offline()
        override suspend fun apiGetRankByUser(platform: String, platformId: String): Result<JsonObject> = offline()
        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> = offline()
        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<GitHubRelease> = offline()
    }
}
