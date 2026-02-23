package org.kasumi321.ushio.phitracker.ui.login

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.kasumi321.ushio.phitracker.domain.model.Server

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    // 自动跳转 (已有 token)
    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            onLoginSuccess()
        }
    }

    // 显示错误
    LaunchedEffect(state.error, state.qrError) {
        val msg = state.error ?: state.qrError
        msg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // 检查 token 中，显示加载
    if (state.isCheckingToken) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // 标题
            Text(
                text = "Phigros\nScore Tracker",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "by Ushio",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 服务器选择
            Text(
                text = "选择服务器",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Server.entries.forEach { server ->
                    FilterChip(
                        selected = state.server == server,
                        onClick = { viewModel.updateServer(server) },
                        label = { Text(server.displayName) },
                        enabled = !state.isLoading && state.qrStatus == QrStatus.Idle
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tab 切换: 二维码 | Token
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("扫码登录") },
                    icon = { Icon(Icons.Default.QrCode2, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Token 登录") },
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "login_tab"
            ) { tab ->
                when (tab) {
                    0 -> QrLoginContent(
                        state = state,
                        onStartQrLogin = { viewModel.startQrLogin() },
                        onCancel = { viewModel.cancelQrLogin() }
                    )
                    1 -> TokenLoginContent(
                        state = state,
                        onTokenChange = { viewModel.updateToken(it) },
                        onLogin = { viewModel.login() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// ── QR 码登录 Tab ────────────────────────────────────────────────────

@Composable
private fun QrLoginContent(
    state: LoginUiState,
    onStartQrLogin: () -> Unit,
    onCancel: () -> Unit
) {
    // 清理: 离开页面时取消轮询
    DisposableEffect(Unit) {
        onDispose { onCancel() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state.qrStatus) {
            QrStatus.Idle -> {
                Text(
                    text = "使用 TapTap App 扫描二维码\n快速安全地登录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onStartQrLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("生成二维码")
                }
            }

            QrStatus.Loading -> {
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在生成二维码...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            QrStatus.WaitingScan, QrStatus.Scanned -> {
                // QR 码显示
                state.qrCodeUrl?.let { url ->
                    QrCodeImage(url = url)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 状态文字
                val statusText = if (state.qrStatus == QrStatus.Scanned) {
                    "✅ 已扫描，请在 TapTap 上确认登录"
                } else {
                    "请使用 TapTap App 扫描二维码"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.qrStatus == QrStatus.Scanned)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 倒计时
                Text(
                    text = "剩余时间: ${state.qrRemainingSeconds}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.qrRemainingSeconds <= 30)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = onCancel) {
                    Text("取消")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "⚠️ 请注意，登录 TapTap 可能造成账号及财产损失，\n请在信任来源的情况下扫码登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            QrStatus.Exchanging -> {
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在获取 Token 并同步存档...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            QrStatus.Success -> {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "登录成功!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            QrStatus.Error -> {
                Spacer(modifier = Modifier.height(32.dp))
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.qrError ?: "登录失败",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartQrLogin) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重试")
                }
            }

            QrStatus.Expired -> {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "⏰ 二维码已过期",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartQrLogin) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新生成")
                }
            }
        }
    }
}

// ── Token 登录 Tab ───────────────────────────────────────────────────

@Composable
private fun TokenLoginContent(
    state: LoginUiState,
    onTokenChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Token 输入
        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChange,
            label = { Text("Session Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoading
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 登录按钮
        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = state.token.isNotBlank() && !state.isLoading
        ) {
            AnimatedVisibility(visible = state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            AnimatedVisibility(visible = !state.isLoading) {
                Text("登录并同步")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "请输入 TapTap 的 Session Token\n可通过抓包或浏览器开发者工具获取",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── QR 码图片生成 ────────────────────────────────────────────────────

@Composable
private fun QrCodeImage(url: String) {
    val bitmap = remember(url) { generateQrBitmap(url, 512) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "扫码登录二维码",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "二维码生成失败",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 纯 Android API 生成 QR 码 Bitmap (无第三方依赖)
 * 使用简单的 QR 编码实现
 */
private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        // 使用 Android 内置的 QR 码生成 (通过 zxing-lite 或手动编码)
        // 这里使用一个轻量级的 QR 编码方案
        val encoder = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = encoder.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
