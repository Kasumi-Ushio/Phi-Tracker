package org.kasumi321.ushio.phitracker.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.kasumi321.ushio.phitracker.ui.components.CenteredListItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.BuildConfig
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.ui.window.DialogProperties
import org.kasumi321.ushio.phitracker.ui.home.UpdateCheckState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    themeMode: Int,
    showB30Overflow: Boolean,
    overflowCount: Int,
    onThemeModeChange: (Int) -> Unit,
    onShowB30OverflowChange: (Boolean) -> Unit,
    onOverflowCountChange: (Int) -> Unit,
    onClearHighResCache: () -> Unit,
    onRedownloadIllustrations: () -> Unit,
    isUpdatingData: Boolean = false,
    updateDataProgress: Int = 0,
    updateDataTotal: Int = 0,
    updateDataFileName: String = "",
    updateDataError: String? = null,
    onUpdateSongData: () -> Unit = {},
    onDismissUpdateError: () -> Unit = {},
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit,
    includePreRelease: Boolean = false,
    updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    onCheckForUpdate: () -> Unit = {},
    onIncludePreReleaseChange: (Boolean) -> Unit = {},
    onDismissUpdateResult: () -> Unit = {},
    tip: String = "",
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRedownloadDialog by remember { mutableStateOf(false) }
    var showUpdateDataDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isExporting = true
                try {
                    withContext(Dispatchers.IO) {
                        val process = Runtime.getRuntime().exec("logcat -d -v time")
                        process.inputStream.use { input ->
                            context.contentResolver.openOutputStream(it)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isExporting = false
                }
            }
        }
    }

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

            CategoryTitle("程序更新")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("接收预发布版本更新", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "启用后将包含 Pre-Release 版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = includePreRelease,
                    onCheckedChange = { onIncludePreReleaseChange(it) }
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

            CenteredListItem(
                headlineContent = { Text("更新曲目数据") },
                supportingContent = { Text("下载最新的 Phigros 全曲目信息") },
                leadingContent = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                modifier = Modifier.clickable { showUpdateDataDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            if (BuildConfig.DEBUG) {
                CategoryTitle("调试选项 (仅 Debug)")

                CenteredListItem(
                    headlineContent = { Text("导出运行日志") },
                    supportingContent = { 
                        if (isExporting) Text("正在导出...") 
                        else Text("导出 logcat 日志到本地文件以供排查问题") 
                    },
                    leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = !isExporting) {
                        createDocumentLauncher.launch("phitracker_debug_log.txt")
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            CategoryTitle("关于")

            CenteredListItem(
                headlineContent = { Text("检查更新") },
                supportingContent = {
                    when (updateCheckState) {
                        is UpdateCheckState.Checking -> Text("正在检查...")
                        is UpdateCheckState.NoUpdate -> Text("已是最新版本")
                        is UpdateCheckState.Error -> Text("检查失败: ${updateCheckState.message}")
                        else -> Text("从 GitHub Releases 检查是否有新版本")
                    }
                },
                leadingContent = {
                    if (updateCheckState is UpdateCheckState.Checking) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                    }
                },
                modifier = Modifier.clickable(enabled = updateCheckState !is UpdateCheckState.Checking) {
                    onCheckForUpdate()
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

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

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清理缓存") },
            text = { Text("确定要清理所有高清曲绘缓存吗？\n之前下载的曲绘缩略图不会随缓存清理删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    onClearHighResCache()
                    Toast.makeText(context, "清理完成", Toast.LENGTH_SHORT).show()
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

    // 更新可用弹窗
    if (updateCheckState is UpdateCheckState.Available) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { onDismissUpdateResult() },
            title = { Text("发现新版本") },
            text = {
                Column {
                    Text("最新版本: ${updateCheckState.version}")
                    if (updateCheckState.body.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateCheckState.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissUpdateResult()
                    uriHandler.openUri(updateCheckState.htmlUrl)
                }) {
                    Text("前往下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissUpdateResult() }) {
                    Text("稍后再说")
                }
            }
        )
    }

    if (showRedownloadDialog) {
        AlertDialog(
            onDismissRequest = { showRedownloadDialog = false },
            title = { Text("重新下载") },
            text = { Text("确定要删除本地所有曲绘信息吗？\n如点击确定，本应用将自动退出，下次进入应用时将自动重新唤起预加载窗口。") },
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

    if (showUpdateDataDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDataDialog = false },
            title = { Text("更新曲目数据") },
            text = { Text("将从远程仓库下载最新的曲目数据，之后将自动刷新本地曲目数据。\n下载完毕后，推荐重新下载所有曲绘。") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDataDialog = false
                    onUpdateSongData()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDataDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (isUpdatingData) {
        AlertDialog(
            onDismissRequest = { /* 不允许取消 */ },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("更新曲目数据") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "正在下载: $updateDataFileName ($updateDataProgress/$updateDataTotal)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val progress = if (updateDataTotal > 0) updateDataProgress.toFloat() / updateDataTotal else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (updateDataError != null) {
        AlertDialog(
            onDismissRequest = onDismissUpdateError,
            title = { Text("更新失败") },
            text = { Text("发生了错误：\n$updateDataError") },
            confirmButton = {
                TextButton(onClick = onDismissUpdateError) {
                    Text("确定")
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
