package org.kasumi321.ushio.phitracker.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kasumi321.ushio.phitracker.BuildConfig
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.kasumi321.ushio.phitracker.ui.home.UpdateCheckState
import org.kasumi321.ushio.phitracker.utils.CrashReportExporter
import org.kasumi321.ushio.phitracker.utils.RuntimeLogCollector
import org.kasumi321.ushio.phitracker.utils.RuntimeLogExporter

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
    autoCheckUpdate: Boolean = true,
    updateCheckState: UpdateCheckState = UpdateCheckState.Idle,
    onCheckForUpdate: () -> Unit = {},
    onIncludePreReleaseChange: (Boolean) -> Unit = {},
    onAutoCheckUpdateChange: (Boolean) -> Unit = {},
    onDismissUpdateResult: () -> Unit = {},
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
    tip: String = "",
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showRedownloadDialog by remember { mutableStateOf(false) }
    var showUpdateDataDialog by remember { mutableStateOf(false) }
    var showApiRiskDialog by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { mutableStateOf(hasCrashNotificationPermission(context)) }
    var showNotificationGuideDialog by remember { mutableStateOf(false) }

    val requestNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationPermissionGranted = hasCrashNotificationPermission(context)
        if (!granted) {
            Toast.makeText(context, "通知权限未开启，崩溃提示可能无法显示", Toast.LENGTH_SHORT).show()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionGranted = hasCrashNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("phitracker_settings", android.content.Context.MODE_PRIVATE)
        val guideShown = prefs.getBoolean("crash_notification_guide_shown", false)
        if (!guideShown && !notificationPermissionGranted) {
            showNotificationGuideDialog = true
            prefs.edit().putBoolean("crash_notification_guide_shown", true).apply()
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isExporting = true
                try {
                    val exportText = withContext(Dispatchers.IO) {
                        CrashReportExporter.buildExportText(context)
                    }
                    if (exportText.isBlank()) {
                        Toast.makeText(context, "暂无可导出的崩溃日志", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                            writer.write(exportText)
                        }
                    }
                    Toast.makeText(context, "崩溃日志导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val createRuntimeLogDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            coroutineScope.launch {
                isExporting = true
                try {
                    val exportText = withContext(Dispatchers.IO) {
                        RuntimeLogExporter.buildExportText(context)
                    }
                    if (exportText.isBlank()) {
                        Toast.makeText(context, "暂无可导出的运行日志", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer ->
                            writer.write(exportText)
                        }
                    }
                    Toast.makeText(context, "运行日志导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
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
                        valueRange = 1f..30f,
                        steps = 28,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启动时自动检查更新", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "开启后每次启动会自动检查 GitHub Releases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCheckUpdate,
                    onCheckedChange = { onAutoCheckUpdateChange(it) }
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

            CategoryTitle("调试选项")

            CenteredListItem(
                headlineContent = { Text("崩溃通知权限") },
                supportingContent = {
                    if (notificationPermissionGranted) {
                        Text("已开启，可在应用崩溃后显示提醒通知")
                    } else {
                        Text("未开启，建议开启以便在崩溃后及时收到提示")
                    }
                },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                    notificationPermissionGranted = hasCrashNotificationPermission(context)
                }
            )

            if (BuildConfig.DEBUG) {
                CenteredListItem(
                    headlineContent = { Text("导出崩溃日志") },
                    supportingContent = {
                        if (isExporting) Text("正在导出...")
                        else Text("导出崩溃日志，当 App 崩溃时可用于 issue 反馈")
                    },
                    leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = !isExporting) {
                        if (CrashReportExporter.hasReports(context)) {
                            createDocumentLauncher.launch("phitracker_crash_reports.txt")
                        } else {
                            Toast.makeText(context, "暂无可导出的崩溃日志", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                CenteredListItem(
                    headlineContent = { Text("导出运行日志") },
                    supportingContent = {
                        if (isExporting) Text("正在导出...")
                        else Text("导出运行日志，用于复现问题时分析")
                    },
                    leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = !isExporting) {
                        if (RuntimeLogExporter.hasLogs(context)) {
                            createRuntimeLogDocumentLauncher.launch("phitracker_runtime_logs.txt")
                        } else {
                            Toast.makeText(context, "暂无可导出的运行日志", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                CenteredListItem(
                    headlineContent = { Text("清理运行日志") },
                    supportingContent = { Text("清空目前为止所有的运行日志与崩溃日志") },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                    modifier = Modifier.clickable(enabled = !isExporting) {
                        showClearLogsDialog = true
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

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

    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text("清理运行日志") },
            text = { Text("将删除当前设备上的全部运行日志与崩溃日志，此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLogsDialog = false
                        coroutineScope.launch {
                            isExporting = true
                            try {
                                val (runtimeDeleted, crashDeleted) = withContext(Dispatchers.IO) {
                                    val runtime = RuntimeLogCollector.clearLogs(context)
                                    val crash = CrashReportExporter.clearReports(context)
                                    runtime to crash
                                }
                                Toast.makeText(
                                    context,
                                    "已清空日志：运行日志 $runtimeDeleted 个，崩溃日志 $crashDeleted 个",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    }
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) {
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

    if (showNotificationGuideDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationGuideDialog = false },
            title = { Text("开启崩溃通知") },
            text = {
                Text("为确保应用崩溃后能及时提醒并引导反馈，建议开启通知权限。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNotificationGuideDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    }
                ) {
                    Text("立即开启")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationGuideDialog = false }) {
                    Text("稍后")
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

private fun hasCrashNotificationPermission(context: android.content.Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
