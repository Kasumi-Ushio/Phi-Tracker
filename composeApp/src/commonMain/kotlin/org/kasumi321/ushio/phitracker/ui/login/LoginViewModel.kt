package org.kasumi321.ushio.phitracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.QrLoginPollResult
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.model.SyncMode
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.repository.QrLoginRepository
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import org.kasumi321.ushio.phitracker.ui.utils.UiText
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.login_error_credential_expired
import phitracker.composeapp.generated.resources.login_error_empty_token
import phitracker.composeapp.generated.resources.login_error_expired_no_save
import phitracker.composeapp.generated.resources.login_error_sync_failed
import phitracker.composeapp.generated.resources.login_qr_auth_failed
import phitracker.composeapp.generated.resources.login_qr_fetch_failed
import phitracker.composeapp.generated.resources.login_qr_status_failed
import phitracker.composeapp.generated.resources.login_qr_sync_failed

/** QR 扫码状态 */
enum class QrStatus {
    Idle,
    Loading,
    WaitingScan,
    Scanned,
    Exchanging,
    Success,
    Error,
    Expired
}

data class LoginUiState(
    val token: String = "",
    val server: Server = Server.CN,
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val isLoggedIn: Boolean = false,
    val isCheckingToken: Boolean = true,
    val qrCodeUrl: String? = null,
    val qrStatus: QrStatus = QrStatus.Idle,
    val qrError: UiText? = null,
    val qrRemainingSeconds: Int = 0
)

class LoginViewModel(
    private val repository: PhigrosRepository,
    private val syncSaveUseCase: SyncSaveUseCase,
    private val qrLoginRepository: QrLoginRepository,
    private val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var qrPollingJob: Job? = null

    init {
        checkExistingToken()
    }

    private fun checkExistingToken() {
        viewModelScope.launch {
            val saved = repository.getSessionToken()
            if (saved == null) {
                _uiState.update { it.copy(isCheckingToken = false) }
                AppLogger.event("login", "state_checked", mapOf("tokenPresent" to "false", "loggedIn" to "false"))
                return@launch
            }

            val (token, server) = saved

            // A cached save means this device has synced before, so the silent
            // startup sync must diff against the stored records (Refresh) instead
            // of overwriting them (Bootstrap) — Bootstrap writes no snapshot or
            // history and would swallow score changes made since the last launch.
            val cachedSave = repository.getCachedSave().first()

            // Try to refresh the session and save from the network.
            val validateResult = repository.validateToken(token, server)
            val syncResult = if (validateResult.isSuccess) {
                val mode = if (cachedSave != null) SyncMode.Refresh else SyncMode.Bootstrap
                syncSaveUseCase(token, server, mode)
            } else {
                null
            }
            val onlineRefreshOk = validateResult.isSuccess && syncResult?.isSuccess == true

            if (onlineRefreshOk) {
                _uiState.update {
                    it.copy(token = token, server = server, isCheckingToken = false, isLoggedIn = true)
                }
                AppLogger.event(
                    "login",
                    "state_checked",
                    mapOf("tokenPresent" to "true", "loggedIn" to "true", "source" to "online")
                )
                return@launch
            }

            // The network refresh failed (offline, or the server rejected the call). Instead of
            // forcing a logout, stay signed in with the local session + cached save when one is
            // available — only the total absence of local data sends the user back to login.
            if (cachedSave != null) {
                _uiState.update {
                    it.copy(token = token, server = server, isCheckingToken = false, isLoggedIn = true)
                }
                AppLogger.event(
                    "login",
                    "state_checked",
                    mapOf("tokenPresent" to "true", "loggedIn" to "true", "source" to "local")
                )
                return@launch
            }

            _uiState.update {
                it.copy(
                    token = token,
                    server = server,
                    isCheckingToken = false,
                    isLoggedIn = false,
                    error = UiText.Res(Res.string.login_error_expired_no_save)
                )
            }
            AppLogger.event("login", "state_checked", mapOf("tokenPresent" to "true", "loggedIn" to "false"))
        }
    }

    fun updateToken(token: String) {
        _uiState.update { it.copy(token = token.trim(), error = null) }
    }

    fun updateServer(server: Server) {
        _uiState.update { it.copy(server = server, error = null) }
    }

    fun login() {
        val state = _uiState.value
        if (state.token.isBlank()) {
            _uiState.update { it.copy(error = UiText.Res(Res.string.login_error_empty_token)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val validateResult = repository.validateToken(state.token, state.server)
            if (validateResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UiText.Res(Res.string.login_error_credential_expired)
                    )
                }
                return@launch
            }

            repository.saveSessionToken(state.token, state.server)

            val syncResult = syncSaveUseCase(state.token, state.server, SyncMode.Bootstrap)
            if (syncResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UiText.Res(Res.string.login_error_sync_failed)
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
        }
    }

    fun startQrLogin() {
        cancelQrLogin()

        val server = _uiState.value.server
        _uiState.update {
            it.copy(qrStatus = QrStatus.Loading, qrError = null, qrCodeUrl = null)
        }

        qrPollingJob = viewModelScope.launch {
            var failureMessage = UiText.Res(Res.string.login_qr_fetch_failed)
            try {
                val challenge = qrLoginRepository.requestChallenge(server)
                val expiresIn = remainingSeconds(challenge.expiresAt)

                _uiState.update {
                    it.copy(
                        qrCodeUrl = challenge.qrUrl,
                        qrStatus = QrStatus.WaitingScan,
                        qrRemainingSeconds = expiresIn
                    )
                }

                while (clockMillis() < challenge.expiresAt) {
                    _uiState.update { it.copy(qrRemainingSeconds = remainingSeconds(challenge.expiresAt)) }

                    failureMessage = UiText.Res(Res.string.login_qr_status_failed)
                    when (val result = qrLoginRepository.poll(challenge.id)) {
                        is QrLoginPollResult.Authorized -> {
                            _uiState.update { it.copy(qrStatus = QrStatus.Exchanging) }
                            failureMessage = UiText.Res(Res.string.login_qr_auth_failed)
                            val sessionToken = qrLoginRepository.exchangeForSessionToken(result.authorizationId)

                            repository.saveSessionToken(sessionToken, server)
                            val syncResult = syncSaveUseCase(sessionToken, server, SyncMode.Bootstrap)
                            if (syncResult.isFailure) {
                                _uiState.update {
                                    it.copy(
                                        qrStatus = QrStatus.Error,
                                        qrError = UiText.Res(Res.string.login_qr_sync_failed)
                                    )
                                }
                                return@launch
                            }

                            _uiState.update {
                                it.copy(
                                    qrStatus = QrStatus.Success,
                                    isLoggedIn = true,
                                    token = sessionToken
                                )
                            }
                            return@launch
                        }
                        QrLoginPollResult.AuthorizationWaiting -> {
                            _uiState.update { it.copy(qrStatus = QrStatus.Scanned) }
                        }
                        QrLoginPollResult.Pending -> Unit
                    }

                    delay(2000)
                }

                _uiState.update { it.copy(qrStatus = QrStatus.Expired, qrRemainingSeconds = 0) }

            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                _uiState.update {
                    it.copy(
                        qrStatus = QrStatus.Error,
                        qrError = failureMessage
                    )
                }
            }
        }
    }

    fun cancelQrLogin() {
        qrPollingJob?.cancel()
        qrPollingJob = null
        _uiState.update {
            it.copy(qrStatus = QrStatus.Idle, qrCodeUrl = null, qrError = null)
        }
    }

    private fun remainingSeconds(expiresAt: Long): Int =
        (((expiresAt - clockMillis()).coerceAtLeast(0) + 999L) / 1_000L).toInt()

    fun clearError() {
        _uiState.update { it.copy(error = null, qrError = null) }
    }
}
