package org.kasumi321.ushio.phitracker.ui.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.kasumi321.ushio.phitracker.domain.model.B30ChartTagBatch
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.ChartTagSongData
import org.kasumi321.ushio.phitracker.domain.model.ChartTagTreeNode
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.domain.model.GameProgress
import org.kasumi321.ushio.phitracker.domain.model.QrAuthorizationId
import org.kasumi321.ushio.phitracker.domain.model.QrChallengeId
import org.kasumi321.ushio.phitracker.domain.model.QrLoginChallenge
import org.kasumi321.ushio.phitracker.domain.model.QrLoginPollResult
import org.kasumi321.ushio.phitracker.domain.model.ReleaseInfo
import org.kasumi321.ushio.phitracker.domain.model.Save
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SongSyncHistoryEntry
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.model.SyncSaveResult
import org.kasumi321.ushio.phitracker.domain.model.SyncSnapshot
import org.kasumi321.ushio.phitracker.domain.model.UserProfile
import org.kasumi321.ushio.phitracker.domain.model.UserSettings
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.QrLoginRepository
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.ui.ViewModelTestLifecycle
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LoginViewModelQrRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelLifecycle = ViewModelTestLifecycle()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = viewModelLifecycle.tearDown(dispatcher)

    @Test
    fun qrHappyPathUsesTwoSecondCadencePersistsThenBootstrapsAndSucceeds() = runTest(dispatcher) {
        val phigros = FakePhigrosRepository(syncOk = true)
        val qr = FakeQrRepository(
            expiresAt = 30_000,
            polls = ArrayDeque(
                listOf(
                    QrLoginPollResult.Pending,
                    QrLoginPollResult.AuthorizationWaiting,
                    QrLoginPollResult.Authorized(QrAuthorizationId("authorization-opaque"))
                )
            )
        )
        val vm = viewModelLifecycle.track(
            LoginViewModel(phigros, SyncSaveUseCase(phigros), qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()
        vm.updateServer(Server.GLOBAL)

        vm.startQrLogin()
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        assertEquals("https://qr.test/challenge", vm.uiState.value.qrCodeUrl)
        assertEquals(listOf(0L), qr.pollTimes)

        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(listOf(0L), qr.pollTimes)
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(0L, 2_000L), qr.pollTimes)
        assertEquals(QrStatus.Scanned, vm.uiState.value.qrStatus)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(QrStatus.Success, vm.uiState.value.qrStatus)
        assertTrue(vm.uiState.value.isLoggedIn)
        assertEquals("qr-session", vm.uiState.value.token)
        assertEquals(listOf("qr-session" to Server.GLOBAL), phigros.persistedTokens)
        assertEquals(listOf(SyncMode.Bootstrap), phigros.syncModes)
        assertEquals(listOf(QrAuthorizationId("authorization-opaque")), qr.exchanges)
    }

    @Test
    fun qrExpiryUsesVirtualTimeAndEndsExpiredWithoutPersisting() = runTest(dispatcher) {
        val phigros = FakePhigrosRepository(syncOk = true)
        val qr = FakeQrRepository(expiresAt = 4_000)
        val vm = viewModelLifecycle.track(
            LoginViewModel(phigros, SyncSaveUseCase(phigros), qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertEquals(listOf(0L), qr.pollTimes)
        advanceTimeBy(3_999)
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(QrStatus.Expired, vm.uiState.value.qrStatus)
        assertEquals(0, vm.uiState.value.qrRemainingSeconds)
        assertEquals(listOf(0L, 2_000L), qr.pollTimes)
        assertTrue(phigros.persistedTokens.isEmpty())
    }

    @Test
    fun cancellingQrLoginWhilePollingLeavesIdleInsteadOfError() = runTest(dispatcher) {
        val phigros = FakePhigrosRepository(syncOk = true)
        val qr = FakeQrRepository(expiresAt = 30_000, suspendPoll = true)
        val vm = viewModelLifecycle.track(
            LoginViewModel(phigros, SyncSaveUseCase(phigros), qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertEquals(1, qr.pollTimes.size)
        vm.cancelQrLogin()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus)
        runCurrent()

        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus, "cancelled QR login must not overwrite Idle with Error")
        assertEquals(null, vm.uiState.value.qrError)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, qr.pollTimes.size)
        assertTrue(phigros.persistedTokens.isEmpty())
    }

    @Test
    fun cancelledOldQrLoginCannotOverwriteNewAttemptState() = runTest(dispatcher) {
        val phigros = FakePhigrosRepository(syncOk = true)
        val qr = FakeQrRepository(expiresAt = 30_000, suspendPoll = true, suspendRequestAttempt = 2)
        val vm = viewModelLifecycle.track(
            LoginViewModel(phigros, SyncSaveUseCase(phigros), qr) { testScheduler.currentTime }
        )
        advanceUntilIdle()

        vm.startQrLogin()
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)

        vm.startQrLogin()
        assertEquals(QrStatus.Loading, vm.uiState.value.qrStatus)
        runCurrent()
        assertEquals(
            QrStatus.Loading,
            vm.uiState.value.qrStatus,
            "cancelled old QR login must not overwrite the newer attempt's Loading state"
        )

        vm.cancelQrLogin()
        runCurrent()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus)

        vm.startQrLogin()
        runCurrent()
        assertEquals(QrStatus.WaitingScan, vm.uiState.value.qrStatus)
        vm.cancelQrLogin()
        runCurrent()
        assertEquals(QrStatus.Idle, vm.uiState.value.qrStatus)
        assertTrue(phigros.persistedTokens.isEmpty())
    }

    @Test
    fun qrRequestPollExchangeAndSyncFailuresMapToErrorWithoutCredentialLeak() = runTest(dispatcher) {
        suspend fun assertError(qr: FakeQrRepository, phigros: FakePhigrosRepository = FakePhigrosRepository(true)) {
            val vm = viewModelLifecycle.track(
                LoginViewModel(phigros, SyncSaveUseCase(phigros), qr) { testScheduler.currentTime }
            )
            advanceUntilIdle()
            vm.startQrLogin()
            advanceUntilIdle()
            assertEquals(QrStatus.Error, vm.uiState.value.qrStatus)
            latestQrError = vm.uiState.value.qrError
            val publicText = listOfNotNull(vm.uiState.value.qrError, vm.uiState.value.qrCodeUrl).joinToString()
            assertFalse("device-secret" in publicText)
            assertFalse("access-secret" in publicText)
        }

        assertError(FakeQrRepository(requestFailure = IllegalStateException("request DEVICE_UI_SECRET")))
        assertEquals("获取二维码失败，请重试", latestQrError)
        assertFalse("DEVICE_UI_SECRET" in latestQrError.orEmpty())
        assertError(FakeQrRepository(pollFailure = IllegalStateException("poll ACCESS_UI_SECRET")))
        assertEquals("二维码状态查询失败，请重试", latestQrError)
        assertFalse("ACCESS_UI_SECRET" in latestQrError.orEmpty())
        assertError(
            FakeQrRepository(
                polls = ArrayDeque(listOf(QrLoginPollResult.Authorized(QrAuthorizationId("auth")))),
                exchangeFailure = IllegalStateException("exchange SESSION_UI_SECRET")
            )
        )
        assertEquals("二维码授权失败，请重试", latestQrError)
        assertFalse("SESSION_UI_SECRET" in latestQrError.orEmpty())
        val syncRepo = FakePhigrosRepository(syncOk = false)
        assertError(
            FakeQrRepository(polls = ArrayDeque(listOf(QrLoginPollResult.Authorized(QrAuthorizationId("auth"))))),
            syncRepo
        )
        assertEquals(listOf("qr-session" to Server.CN), syncRepo.persistedTokens)
        assertEquals(listOf(SyncMode.Bootstrap), syncRepo.syncModes)
    }

    private inner class FakeQrRepository(
        private val expiresAt: Long = 30_000,
        private val polls: ArrayDeque<QrLoginPollResult> = ArrayDeque(),
        private val requestFailure: Throwable? = null,
        private val pollFailure: Throwable? = null,
        private val exchangeFailure: Throwable? = null,
        private val suspendPoll: Boolean = false,
        private val suspendRequestAttempt: Int? = null
    ) : QrLoginRepository {
        val pollTimes = mutableListOf<Long>()
        val exchanges = mutableListOf<QrAuthorizationId>()
        val requestedServers = mutableListOf<Server>()
        private var requestAttempts = 0

        override suspend fun requestChallenge(server: Server): QrLoginChallenge {
            requestAttempts += 1
            if (requestAttempts == suspendRequestAttempt) awaitCancellation()
            requestFailure?.let { throw it }
            requestedServers += server
            return QrLoginChallenge(QrChallengeId("challenge-opaque"), "https://qr.test/challenge", expiresAt)
        }

        override suspend fun poll(challengeId: QrChallengeId): QrLoginPollResult {
            pollTimes += dispatcher.scheduler.currentTime
            if (suspendPoll) awaitCancellation()
            pollFailure?.let { throw it }
            return if (polls.isEmpty()) QrLoginPollResult.Pending else polls.removeFirst()
        }

        override suspend fun exchangeForSessionToken(authorizationId: QrAuthorizationId): String {
            exchanges += authorizationId
            exchangeFailure?.let { throw it }
            return "qr-session"
        }
    }

    private var latestQrError: String? = null

    private class FakePhigrosRepository(private val syncOk: Boolean) : PhigrosRepository {
        val persistedTokens = mutableListOf<Pair<String, Server>>()
        val syncModes = mutableListOf<SyncMode>()
        private fun offline() = Result.failure<Nothing>(RuntimeException("network unavailable"))
        override suspend fun validateToken(sessionToken: String, server: Server): Result<UserProfile> = offline()
        override suspend fun syncSave(sessionToken: String, server: Server, mode: SyncMode): Result<SyncSaveResult> {
            syncModes += mode
            return if (syncOk) Result.success(SyncSaveResult(minimalSave(), 1L, 0, false)) else offline()
        }
        override fun getCachedSave(): Flow<Save?> = flowOf(null)
        override fun getUserProfile(): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveSessionToken(token: String, server: Server) { persistedTokens += token to server }
        override suspend fun getSessionToken(): Pair<String, Server>? = null
        override suspend fun clearData() = Unit
        override suspend fun clearTokenSync() = Unit
        override suspend fun getClearCountsByDifficulty(): Map<Difficulty, Int> = emptyMap()
        override suspend fun getTotalFullComboCount(): Int = 0
        override suspend fun getTotalPhiCount(): Int = 0
        override fun observeSyncSnapshots(): Flow<List<SyncSnapshot>> = flowOf(emptyList())
        override suspend fun getSyncSnapshotsOnce(): List<SyncSnapshot> = emptyList()
        override fun observeSongSyncHistory(songId: String): Flow<List<SongSyncHistoryEntry>> = flowOf(emptyList())
        override suspend fun getSyncHistoryForSnapshot(snapshotId: Long): List<SongSyncHistoryEntry> = emptyList()
        override suspend fun apiTest(): Result<JsonObject> = offline()
        override suspend fun apiGetBindInfo(platform: String, platformId: String): Result<JsonObject> = offline()
        override suspend fun getSongApiDetail(key: org.kasumi321.ushio.phitracker.domain.model.ApiDetailCacheKey): Result<org.kasumi321.ushio.phitracker.domain.model.SongApiDetail> = offline()
        override suspend fun apiGetRksAbove(rks: Float): Result<JsonObject> = offline()
        override suspend fun apiGetSaveHistory(platform: String, platformId: String, apiUserId: String, request: List<String>): Result<JsonObject> = offline()
        override suspend fun apiGetRankByUser(platform: String, platformId: String, apiUserId: String): Result<JsonObject> = offline()
        override suspend fun apiGetRankByPosition(position: Int): Result<JsonObject> = offline()
        override suspend fun getChartTagTree(): Result<List<ChartTagTreeNode>> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getChartTags(songId: String, difficulty: Difficulty): Result<ChartTagSongData> =
            Result.failure(UnsupportedOperationException())

        override suspend fun getMyChartTagVotes(
            songId: String,
            difficulty: Difficulty,
            platform: String,
            platformId: String,
            apiUserId: String,
            apiToken: String?
        ): Result<Set<String>> = Result.failure(UnsupportedOperationException())

        override suspend fun getB30ChartTags(records: List<BestRecord>): Result<B30ChartTagBatch> =
            Result.failure(UnsupportedOperationException())

        override suspend fun voteChartTags(
            songId: String,
            difficulty: Difficulty,
            primaryTags: List<String>,
            secondaryTags: List<String>,
            platform: String,
            platformId: String,
            apiUserId: String,
            apiToken: String
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        override suspend fun fetchLatestRelease(includePreRelease: Boolean): Result<ReleaseInfo> = offline()
    }

    private companion object {
        fun minimalSave() = Save(
            gameRecord = emptyMap(),
            gameProgress = GameProgress(
                false, false, false, false, "", 0, 0, emptyList(), 0, 0, 0, 0,
                null, null, null, null, null
            ),
            user = UserSettings(false, "", "", ""),
            summary = null
        )
    }
}
