package org.kasumi321.ushio.phitracker.ui.login

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.data.api.GitHubRelease
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.platform.ApiCrypto
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repo: PhigrosRepository,
        qrLoginApi: TapTapQrLoginApi = TapTapQrLoginApi(HttpClient(MockEngine { respond("") }))
    ): LoginViewModel = LoginViewModel(repo, SyncSaveUseCase(repo), qrLoginApi)

    @Test
    fun noSavedTokenGoesToLogin() = runTest(dispatcher) {
        val vm = viewModel(FakeRepo(savedToken = null))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertFalse(vm.uiState.value.isLoggedIn)
    }

    @Test
    fun onlineValidateAndSyncSuccessLogsIn() = runTest(dispatcher) {
        val repo = FakeRepo(savedToken = "t" to Server.CN, validateOk = true, syncOk = true)
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCheckingToken)
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals(listOf(SyncMode.Bootstrap), repo.syncModes)
        assertEquals("t", vm.uiState.value.token)
        assertEquals(Server.CN, vm.uiState.value.server)
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

    @Test
    fun manualTokenLoginPersistsThenShowsHomeAfterSync() = runTest(dispatcher) {
        val repo = FakeRepo(savedToken = null, validateOk = true, syncOk = true)
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.updateToken("  manual-token  ")
        vm.updateServer(Server.GLOBAL)
        vm.login()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoggedIn)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("manual-token", vm.uiState.value.token)
        assertEquals(listOf("manual-token" to Server.GLOBAL), repo.persistedTokens)
        assertEquals(listOf(SyncMode.Bootstrap), repo.syncModes)
    }

    @Test
    fun manualTokenLoginFailureKeepsUserAtLoginAndDoesNotPersist() = runTest(dispatcher) {
        val repo = FakeRepo(savedToken = null, validateOk = false, syncOk = true)
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.updateToken("invalid-token")
        vm.login()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoggedIn)
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
        assertTrue(repo.persistedTokens.isEmpty())
    }

    @Test
    fun qrLoginAdvancesFromChallengeThroughScanExchangeAndSuccessAtTwoSecondCadence() = runTest(dispatcher) {
        val firstPoll = CompletableDeferred<Unit>()
        val profileRequest = CompletableDeferred<Unit>()
        var pollAttempts = 0
        val repo = FakeRepo(savedToken = null, syncOk = true)
        val api = TapTapQrLoginApi(
            HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = this@LoginViewModelTest.dispatcher
                reuseHandlers = true
                addHandler { request -> when (request.url.encodedPath) {
                    "/oauth2/v1/device/code" -> respond(
                        """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    "/oauth2/v1/token" -> {
                        if (pollAttempts++ == 0) firstPoll.await()
                        respond(
                            if (pollAttempts == 1) {
                                """{"success":false,"data":{"error":"authorization_waiting"}}"""
                            } else {
                                """{"success":true,"data":{"kid":"kid","access_token":"access","token_type":"bearer","mac_key":"mac","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}"""
                            },
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    "/account/profile/v1" -> {
                        profileRequest.await()
                        respond(
                            """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    "/1.1/users" -> respond(
                        """{"sessionToken":"qr-session"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    else -> error("Unexpected QR request: ${request.url}")
                } }
            })) { install(ContentNegotiation) { json() } },
            apiCrypto = FakeApiCrypto
        )
        val vm = viewModel(repo, api)
        advanceUntilIdle()

        vm.startQrLogin()
        assertEquals(QrStatus.Loading, vm.uiState.value.qrStatus)
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        assertEquals(30, vm.uiState.value.qrRemainingSeconds)

        firstPoll.complete(Unit)
        runCurrent()
        assertEquals(QrStatus.Scanned, vm.uiState.value.qrStatus)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, pollAttempts)
        assertEquals(QrStatus.Scanned, vm.uiState.value.qrStatus)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, pollAttempts)
        assertEquals(QrStatus.Exchanging, vm.uiState.value.qrStatus)

        profileRequest.complete(Unit)
        advanceUntilIdle()
        assertEquals(QrStatus.Success, vm.uiState.value.qrStatus, vm.uiState.value.qrError)
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals("qr-session", vm.uiState.value.token)
        assertEquals(listOf("qr-session" to Server.CN), repo.persistedTokens)
        assertEquals(listOf(SyncMode.Bootstrap), repo.syncModes)
    }

    @Test
    fun qrLoginExpiresOrFailsAndCanReturnToIdle() = runTest(dispatcher) {
        val expiredApi = TapTapQrLoginApi(
            HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = this@LoginViewModelTest.dispatcher
                reuseHandlers = true
                addHandler { request ->
                if (request.url.encodedPath != "/oauth2/v1/device/code") error("Unexpected QR request")
                respond(
                    """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":0}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                ) }
            })) { install(ContentNegotiation) { json() } },
            apiCrypto = FakeApiCrypto
        )
        val expiredVm = viewModel(FakeRepo(savedToken = null), expiredApi)
        advanceUntilIdle()
        expiredVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Expired, expiredVm.uiState.value.qrStatus)
        assertEquals(0, expiredVm.uiState.value.qrRemainingSeconds)
        expiredVm.cancelQrLogin()
        assertEquals(QrStatus.Idle, expiredVm.uiState.value.qrStatus)

        val failingVm = viewModel(
            FakeRepo(savedToken = null),
            TapTapQrLoginApi(
                HttpClient(MockEngine(MockEngineConfig().apply {
                    dispatcher = this@LoginViewModelTest.dispatcher
                    addHandler { error("request failure") }
                })) { install(ContentNegotiation) { json() } },
                apiCrypto = FakeApiCrypto
            )
        )
        advanceUntilIdle()
        failingVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Error, failingVm.uiState.value.qrStatus)
        assertNotNull(failingVm.uiState.value.qrError)
    }

    @Test
    fun qrLoginCancellationWhilePollingSurfacesErrorWithoutPersisting() = runTest(dispatcher) {
        val pollStarted = CompletableDeferred<Unit>()
        val repo = FakeRepo(savedToken = null, syncOk = true)
        val api = TapTapQrLoginApi(
            HttpClient(MockEngine(MockEngineConfig().apply {
                dispatcher = this@LoginViewModelTest.dispatcher
                reuseHandlers = true
                addHandler { request -> when (request.url.encodedPath) {
                    "/oauth2/v1/device/code" -> respond(
                        """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                    "/oauth2/v1/token" -> {
                        pollStarted.complete(Unit)
                        awaitCancellation()
                    }
                    else -> error("Unexpected QR request: ${request.url}")
                } }
            })) { install(ContentNegotiation) { json() } },
            apiCrypto = FakeApiCrypto
        )
        val vm = viewModel(repo, api)
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertTrue(pollStarted.isCompleted)
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)

        vm.cancelQrLogin()
        runCurrent()
        assertEquals(QrStatus.Error, vm.uiState.value.qrStatus)
        assertNotNull(vm.uiState.value.qrError)
        assertTrue(repo.persistedTokens.isEmpty())
    }

    @Test
    fun qrLoginExchangeFailureAndSyncFailureShowError() = runTest(dispatcher) {
        val exchangeRepo = FakeRepo(savedToken = null, syncOk = true)
        val exchangeVm = viewModel(
            exchangeRepo,
            TapTapQrLoginApi(
                HttpClient(MockEngine(MockEngineConfig().apply {
                    dispatcher = this@LoginViewModelTest.dispatcher
                    reuseHandlers = true
                    addHandler { request -> when (request.url.encodedPath) {
                        "/oauth2/v1/device/code" -> respond(
                            """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/oauth2/v1/token" -> respond(
                            """{"success":true,"data":{"kid":"kid","access_token":"access","token_type":"bearer","mac_key":"mac","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/account/profile/v1" -> respond(
                            """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/1.1/users" -> error("exchange unavailable")
                        else -> error("Unexpected QR request: ${request.url}")
                    } }
                })) { install(ContentNegotiation) { json() } },
                apiCrypto = FakeApiCrypto
            )
        )
        advanceUntilIdle()
        exchangeVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Error, exchangeVm.uiState.value.qrStatus)
        assertTrue(exchangeRepo.persistedTokens.isEmpty())

        val syncRepo = FakeRepo(savedToken = null, syncOk = false)
        val syncVm = viewModel(
            syncRepo,
            TapTapQrLoginApi(
                HttpClient(MockEngine(MockEngineConfig().apply {
                    dispatcher = this@LoginViewModelTest.dispatcher
                    reuseHandlers = true
                    addHandler { request -> when (request.url.encodedPath) {
                        "/oauth2/v1/device/code" -> respond(
                            """{"data":{"device_code":"device-code","qrcode_url":"https://qr.test/challenge","expires_in":30}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/oauth2/v1/token" -> respond(
                            """{"success":true,"data":{"kid":"kid","access_token":"access","token_type":"bearer","mac_key":"mac","mac_algorithm":"hmac-sha-1","scope":"public_profile"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/account/profile/v1" -> respond(
                            """{"data":{"openid":"open-id","name":"Player","avatar":"avatar"}}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        "/1.1/users" -> respond(
                            """{"sessionToken":"qr-session"}""",
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> error("Unexpected QR request: ${request.url}")
                    } }
                })) { install(ContentNegotiation) { json() } },
                apiCrypto = FakeApiCrypto
            )
        )
        advanceUntilIdle()
        syncVm.startQrLogin()
        advanceUntilIdle()
        assertEquals(QrStatus.Error, syncVm.uiState.value.qrStatus)
        assertEquals(listOf("qr-session" to Server.CN), syncRepo.persistedTokens)
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
        val persistedTokens = mutableListOf<Pair<String, Server>>()
        val syncModes = mutableListOf<SyncMode>()
        private fun offline() = Result.failure<Nothing>(RuntimeException("network unavailable"))

        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> =
            if (validateOk) {
                Result.success(UserProfile("id", "nick", "", "", "", 0f, 0, 0, ""))
            } else {
                Result.failure(RuntimeException("network unavailable"))
            }

        override suspend fun syncSave(
            sessionToken: String,
            server: Server,
            mode: SyncMode
        ): Result<SyncSaveResult> {
            syncModes += mode
            return if (syncOk) {
                Result.success(SyncSaveResult(minimalSave(), 1L, 0, false))
            } else {
                Result.failure(RuntimeException("network unavailable"))
            }
        }

        override fun getCachedSave(): Flow<Save?> = flowOf(cachedSave)
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) {
            persistedTokens += token to server
        }
        override suspend fun getSessionToken(): Pair<String, Server>? = savedToken
        override suspend fun clearData() = Unit
        override fun clearTokenSync() = Unit
        override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = emptyMap()
        override suspend fun getTotalFullComboCount(): Int = 0
        override suspend fun getTotalPhiCount(): Int = 0
        override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(emptyList())
        override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = emptyList()
        override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> = flowOf(emptyList())
        override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> = emptyList()

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

    private object FakeApiCrypto : ApiCrypto {
        override fun md5Hex(data: String): String = "fixed-md5"
        override fun hmacSha1(data: String, key: String): ByteArray = byteArrayOf(1, 2, 3)
    }
}
