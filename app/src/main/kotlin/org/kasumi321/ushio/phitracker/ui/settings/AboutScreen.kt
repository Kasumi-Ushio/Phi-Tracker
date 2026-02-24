package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import org.kasumi321.ushio.phitracker.BuildConfig
import org.kasumi321.ushio.phitracker.ui.components.CenteredListItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToDisclaimer: () -> Unit,
    onNavigateToAcknowledgments: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    val buildTime = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(packageInfo.lastUpdateTime))
        } catch (e: Exception) {
            "未知"
        }
    }
    val buildType = if (BuildConfig.DEBUG) "Debug 构建" else "Release 构建"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于 Phi Tracker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text("Phi Tracker", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("一款可以用于读取 Phigros 存档并查分的小工具", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("当前软件版本: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("构建日期: $buildTime", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("构建类型: $buildType", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            HorizontalDivider()
            
            CenteredListItem(
                headlineContent = { Text("项目主页") },
                supportingContent = { Text("在 GitHub 上查看本项目的源代码") },
                leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { 
                    uriHandler.openUri("https://github.com/Kasumi-Ushio/Ushio-Prober-Phigros")
                }
            )

            CenteredListItem(
                headlineContent = { Text("建议和反馈") },
                supportingContent = { Text("遇到 Bug 或有建议？通过 Issues 告诉我们") },
                leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { 
                    uriHandler.openUri("https://github.com/Kasumi-Ushio/Ushio-Prober-Phigros/issues")
                }
            )
            
            CenteredListItem(
                headlineContent = { Text("版权、许可协议与免责声明") },
                supportingContent = { Text("阅读本项目的版权信息及本软件的免责声明") },
                leadingContent = { Icon(Icons.Default.Gavel, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToDisclaimer() }
            )
                        
            CenteredListItem(
                headlineContent = { Text("致谢与参考") },
                supportingContent = { Text("查看特别致谢以及本项目参考引用的外部项目及版权说明") },
                leadingContent = { Icon(Icons.Default.Favorite, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToAcknowledgments() }
            )
            
            CenteredListItem(
                headlineContent = { Text("第三方组件") },
                supportingContent = { Text("查看使用和参考的第三方组件及其许可证信息\n我们使用和参考的所有第三方组件都是自由软件，在此感谢这些组件的开发者们") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToLicenses() }
            )
        }
    }
}
