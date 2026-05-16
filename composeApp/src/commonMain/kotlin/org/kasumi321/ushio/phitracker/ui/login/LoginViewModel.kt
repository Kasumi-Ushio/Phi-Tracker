package org.kasumi321.ushio.phitracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase

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
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val isCheckingToken: Boolean = true,
    val qrCodeUrl: String? = null,
    val qrStatus: QrStatus = QrStatus.Idle,
    val qrError: String? = null,
    val qrRemainingSeconds: Int = 0
)

class LoginViewModel(
    private val repository: PhigrosRepository,
    private val syncSaveUseCase: SyncSaveUseCase,
    private val qrLoginApi: TapTapQrLoginApi
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

            val validateResult = repository.validateToken(token, server)
            if (validateResult.isFailure) {
                _uiState.update {
                    it.copy(
                        token = token,
                        server = server,
                        isCheckingToken = false,
                        isLoggedIn = false,
                        error = "Token 验证失败: ${validateResult.exceptionOrNull()?.message}"
                    )
                }
                AppLogger.event("login", "state_checked", mapOf("tokenPresent" to "true", "loggedIn" to "false"))
                return@launch
            }

            val syncResult = syncSaveUseCase(token, server)
            if (syncResult.isFailure) {
                _uiState.update {
                    it.copy(
                        token = token,
                        server = server,
                        isCheckingToken = false,
                        isLoggedIn = false,
                        error = "存档同步失败: ${syncResult.exceptionOrNull()?.message}"
                    )
                }
                AppLogger.event("login", "state_checked", mapOf("tokenPresent" to "true", "loggedIn" to "false"))
                return@launch
            }

            _uiState.update {
                it.copy(
                    token = token,
                    server = server,
                    isCheckingToken = false,
                    isLoggedIn = true
                )
            }
            AppLogger.event("login", "state_checked", mapOf("tokenPresent" to "true", "loggedIn" to "true"))
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
            _uiState.update { it.copy(error = "请输入 Session Token") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val validateResult = repository.validateToken(state.token, state.server)
            if (validateResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Token 验证失败: ${validateResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }

            repository.saveSessionToken(state.token, state.server)

            val syncResult = syncSaveUseCase(state.token, state.server)
            if (syncResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "存档同步失败: ${syncResult.exceptionOrNull()?.message}"
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
            try {
                val response = qrLoginApi.requestDeviceCode(server)
                val deviceCode = response.data.deviceCode
                val deviceId = response.deviceId
                val expiresIn = response.data.expiresIn

                _uiState.update {
                    it.copy(
                        qrCodeUrl = response.data.qrcodeUrl,
                        qrStatus = QrStatus.WaitingScan,
                        qrRemainingSeconds = expiresIn
                    )
                }

                val startTime = Clock.System.now().toEpochMilliseconds()
                val timeoutMs = expiresIn * 1000L

                while (Clock.System.now().toEpochMilliseconds() - startTime < timeoutMs) {
                    val elapsed = (Clock.System.now().toEpochMilliseconds() - startTime) / 1000
                    val remaining = (expiresIn - elapsed).toInt().coerceAtLeast(0)
                    _uiState.update { it.copy(qrRemainingSeconds = remaining) }

                    val result = qrLoginApi.checkQrCodeResult(deviceCode, deviceId, server)

                    if (result.success && result.data != null) {
                        val tokenData = result.data
                        _uiState.update { it.copy(qrStatus = QrStatus.Exchanging) }

                        val profile = qrLoginApi.getProfile(tokenData, server)

                        val sessionToken = qrLoginApi.exchangeForSessionToken(
                            profile, tokenData, server
                        )

                        repository.saveSessionToken(sessionToken, server)
                        val syncResult = syncSaveUseCase(sessionToken, server)
                        if (syncResult.isFailure) {
                            _uiState.update {
                                it.copy(
                                    qrStatus = QrStatus.Error,
                                    qrError = "存档同步失败: ${syncResult.exceptionOrNull()?.message}"
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

                    } else if (result.error == "authorization_waiting") {
                        _uiState.update { it.copy(qrStatus = QrStatus.Scanned) }
                    }

                    delay(2000)
                }

                _uiState.update { it.copy(qrStatus = QrStatus.Expired, qrRemainingSeconds = 0) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        qrStatus = QrStatus.Error,
                        qrError = e.message ?: "QR 登录失败"
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

    fun clearError() {
        _uiState.update { it.copy(error = null, qrError = null) }
    }
}
