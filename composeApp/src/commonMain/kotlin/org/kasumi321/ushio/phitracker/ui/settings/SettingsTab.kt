package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.kasumi321.ushio.phitracker.data.platform.showPlatformMessage
import org.kasumi321.ushio.phitracker.ui.components.CenteredListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    themeMode: Int,
    showB30Overflow: Boolean,
    overflowCount: Int,
    onThemeModeChange: (Int) -> Unit,
    onShowB30OverflowChange: (Boolean) -> Unit,
    onOverflowCountChange: (Int) -> Unit,
    onClearHighResCache: ((Result<Unit>) -> Unit) -> Unit,
    onRedownloadIllustrations: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    tip: String = "",
    apiEnabled: Boolean = false,
    useApiData: Boolean = false,
    apiPlatform: String = "",
    apiPlatformId: String = "",
    isApiTesting: Boolean = false,
    apiTestMessage: String? = null,
    onApiEnabledChange: (Boolean) -> Unit = {},
    onUseApiDataChange: (Boolean) -> Unit = {},
    onApiPlatformChange: (String) -> Unit = {},
    onApiPlatformIdChange: (String) -> Unit = {},
    onApiTestConnection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRedownloadDialog by remember { mutableStateOf(false) }
    var showApiRiskDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("设置")
                        if (tip.isNotBlank()) {
                            Text(
                                text = tip,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(0.75f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            CategoryTitle("界面与主题")

            val themeOptions = listOf("跟随系统", "始终浅色", "始终深色", "AMOLED 纯黑")
            var expandedTheme by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                Box {
                    TextButton(onClick = { expandedTheme = true }) {
                        Text(themeOptions.getOrElse(themeMode) { themeOptions[0] })
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expandedTheme,
                        onDismissRequest = { expandedTheme = false }
                    ) {
                        themeOptions.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    onThemeModeChange(index)
                                    expandedTheme = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            CategoryTitle("B30 设置")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("显示 Overflow", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "在 B30 页面展示 B27 之后的曲目",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showB30Overflow,
                    onCheckedChange = { onShowB30OverflowChange(it) }
                )
            }

            if (showB30Overflow) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overflow 显示数量")
                        Text(
                            text = overflowCount.toString(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = overflowCount.toFloat(),
                        onValueChange = { onOverflowCountChange(it.roundToInt()) },
                        valueRange = 1f..15f,
                        steps = 13,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            CategoryTitle("查分 API")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用查分 API", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "使用第三方 API 获取额外统计信息",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = apiEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showApiRiskDialog = true
                        } else {
                            onApiEnabledChange(false)
                        }
                    }
                )
            }

            if (apiEnabled) {
                Text(
                    text = "要确定您的平台名称和平台 ID，请向任何一个正在使用 Phi-Plugin 的机器人发送 /tkls 命令以确定。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = apiPlatform,
                        onValueChange = onApiPlatformChange,
                        label = { Text("平台名称") },
                        placeholder = { Text("platform") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = apiPlatformId,
                        onValueChange = onApiPlatformIdChange,
                        label = { Text("平台 ID") },
                        placeholder = { Text("platform_id") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onApiTestConnection,
                    enabled = !isApiTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isApiTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isApiTesting) "测试中..." else "测试连接")
                }

                if (!apiTestMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = apiTestMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("使用查分 API 数据", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "开启后首页和统计优先显示 API 数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useApiData,
                        onCheckedChange = onUseApiDataChange
                    )
                }

                Text(
                    text = "本地同步和 API 同步记录可能存在差异。切换数据源不影响本地数据，本地数据库始终保持更新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            CategoryTitle("数据与缓存")

            CenteredListItem(
                headlineContent = { Text("清理高清曲绘缓存") },
                supportingContent = { Text("释放存储空间，将保留缩略图") },
                leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                modifier = Modifier.clickable { showClearCacheDialog = true }
            )

            CenteredListItem(
                headlineContent = { Text("重新下载所有曲绘") },
                supportingContent = { Text("清空所有图片并强制重启应用") },
                leadingContent = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { showRedownloadDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            CategoryTitle("关于")

            CenteredListItem(
                headlineContent = { Text("关于 Phi Tracker") },
                supportingContent = { Text("了解有关本应用的更多信息，包括作者、版权和第三方组件") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToAbout() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("退出登录")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？所有同步进度将会重置。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showApiRiskDialog) {
        AlertDialog(
            onDismissRequest = { showApiRiskDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("启用查分 API") },
            text = {
                Text(
                    "启用查分 API 将通过第三方接口获取额外统计数据。您的平台名称和平台 ID 会通过加密通道发送至 API 服务器。请确认您了解并接受该风险。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showApiRiskDialog = false
                    onApiEnabledChange(true)
                }) {
                    Text("我已了解并同意")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiRiskDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存") },
            text = { Text("确定要清理所有高清曲绘缓存吗？缩略图将保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    onClearHighResCache { result ->
                        if (result.isSuccess) {
                            showPlatformMessage("清理完成")
                        } else {
                            showPlatformMessage("清理失败: ${result.exceptionOrNull()?.message ?: "未知错误"}")
                        }
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRedownloadDialog) {
        AlertDialog(
            onDismissRequest = { showRedownloadDialog = false },
            title = { Text("重新下载") },
            text = { Text("确定要删除本地所有曲绘信息吗？如点击确定，本应用将自动退出，下次进入应用时将自动重新唤起预加载窗口。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRedownloadDialog = false
                        onRedownloadIllustrations()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRedownloadDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CategoryTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
