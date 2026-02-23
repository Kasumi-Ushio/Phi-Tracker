package org.kasumi321.ushio.phitracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kasumi321.ushio.phitracker.data.api.TapTapQrLoginApi
import org.kasumi321.ushio.phitracker.domain.model.Server
import org.kasumi321.ushio.phitracker.domain.repository.PhigrosRepository
import org.kasumi321.ushio.phitracker.domain.usecase.SyncSaveUseCase
import timber.log.Timber
import javax.inject.Inject

/** QR 扫码状态 */
enum class QrStatus {
    Idle,           // 未开始
    Loading,        // 正在请求设备码
    WaitingScan,    // 等待用户扫码
    Scanned,        // 已扫描，等待确认
    Exchanging,     // 正在换取 sessionToken
    Success,        // 成功
    Error,          // 出错
    Expired         // 二维码已过期
}

data class LoginUiState(
    // -- Token 登录 --
    val token: String = "",
    val server: Server = Server.CN,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val isCheckingToken: Boolean = true,
    // -- QR 登录 --
    val qrCodeUrl: String? = null,
    val qrStatus: QrStatus = QrStatus.Idle,
    val qrError: String? = null,
    val qrRemainingSeconds: Int = 0
)

@HiltViewModel
class LoginViewModel @Inject constructor(
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
            if (saved != null) {
                _uiState.update {
                    it.copy(
                        token = saved.first,
                        server = saved.second,
                        isLoggedIn = true,
                        isCheckingToken = false
                    )
                }
            } else {
                _uiState.update { it.copy(isCheckingToken = false) }
            }
        }
    }

    // ── Token 登录 ───────────────────────────────────────────────────

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

            // 1. 验证 Token
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

            // 2. 保存 Token
            repository.saveSessionToken(state.token, state.server)

            // 3. 同步存档
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

    // ── QR 码登录 ────────────────────────────────────────────────────

    fun startQrLogin() {
        cancelQrLogin()

        val server = _uiState.value.server
        _uiState.update {
            it.copy(qrStatus = QrStatus.Loading, qrError = null, qrCodeUrl = null)
        }

        qrPollingJob = viewModelScope.launch {
            try {
                // Step 1: 请求设备码
                Timber.d("Requesting device code for server: $server")
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

                // Step 2: 轮询检查扫码结果
                val startTime = System.currentTimeMillis()
                val timeoutMs = expiresIn * 1000L

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    // 更新倒计时
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    val remaining = (expiresIn - elapsed).toInt().coerceAtLeast(0)
                    _uiState.update { it.copy(qrRemainingSeconds = remaining) }

                    val result = qrLoginApi.checkQrCodeResult(deviceCode, deviceId, server)

                    if (result.success && result.data != null) {
                        val tokenData = result.data!!
                        // 扫码确认成功，开始换取 sessionToken
                        _uiState.update { it.copy(qrStatus = QrStatus.Exchanging) }

                        // Step 3: 获取 Profile
                        Timber.d("Getting TapTap profile")
                        val profile = qrLoginApi.getProfile(tokenData, server)

                        // Step 4: 换取 sessionToken
                        Timber.d("Exchanging for sessionToken")
                        val sessionToken = qrLoginApi.exchangeForSessionToken(
                            profile, tokenData, server
                        )

                        // 保存并同步
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
                        // 已扫描，等待确认
                        _uiState.update { it.copy(qrStatus = QrStatus.Scanned) }
                    }
                    // authorization_pending = 等待扫描, 继续轮询

                    delay(2000)
                }

                // 超时
                _uiState.update { it.copy(qrStatus = QrStatus.Expired, qrRemainingSeconds = 0) }

            } catch (e: Exception) {
                Timber.e(e, "QR login failed")
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
