package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.kasumi321.ushio.phitracker.ui.settings.SettingsTab

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToB30Image: () -> Unit,
    onNavigateToSongDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tip = remember(selectedTab) { viewModel.getRandomTip() }

    val navItems = listOf(
        BottomNavItem("B30", Icons.Filled.Star, Icons.Outlined.StarBorder),
        BottomNavItem("曲目", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
        BottomNavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    // 登出处理
    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) onLogout()
    }

    // 错误提示
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // ═══════════════════════════════════════════
    // 曲绘预加载对话框 — 阻塞式: 对话框期间不渲染内容
    // ═══════════════════════════════════════════
    if (state.showPreloadDialog) {
        IllustrationPreloadDialog(
            isPreloading = state.isPreloading,
            progress = state.preloadProgress,
            completed = state.preloadCompleted,
            total = state.preloadTotal,
            onStartDownload = { viewModel.startPreloadIllustrations() },
            onDismiss = { viewModel.dismissPreload() }
        )

        // 对话框显示期间, 只显示空白 Scaffold (不渲染任何 Tab 内容)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            // 空白占位 — 不渲染任何内容, 不触发 AsyncImage
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (state.isPreloading) {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }

    // ═══════════════════════════════════════════
    // illustrationReady = true 后, 正常渲染主界面
    // ═══════════════════════════════════════════
    if (!state.illustrationReady) {
        // 还在初始化中 (极短暂的过渡状态)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> B30Tab(
                b30 = state.b30,
                displayRks = state.displayRks,
                nickname = state.nickname,
                challengeModeRank = state.challengeModeRank,
                isSyncing = state.isSyncing,
                onRefresh = { viewModel.refresh() },
                onGenerateImage = onNavigateToB30Image,
                getIllustrationUrl = { viewModel.getIllustrationUrl(it) },
                onSongClick = onNavigateToSongDetail,
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
            1 -> SongsTab(
                songs = state.filteredSongs,
                searchQuery = state.searchQuery,
                onSearchChange = { viewModel.searchSongs(it) },
                availableChapters = state.availableChapters,
                selectedChapter = state.selectedChapter,
                onChapterSelect = { viewModel.filterByChapter(it) },
                selectedDifficulty = state.selectedDifficulty,
                onDifficultySelect = { viewModel.filterByDifficulty(it) },
                minLevel = state.minLevel,
                maxLevel = state.maxLevel,
                onLevelRangeSelect = { min, max -> viewModel.filterByLevelRange(min, max) },
                showFilterSheet = state.showFilterSheet,
                onToggleFilterSheet = { viewModel.toggleFilterSheet(it) },
                onResetFilters = { viewModel.resetFilters() },
                getIllustrationUrl = { viewModel.getIllustrationUrl(it) },
                onSongClick = onNavigateToSongDetail,
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
            2 -> SettingsTab(
                onLogout = { viewModel.logout() },
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
        }
    }
}

/**
 * 曲绘资源预加载对话框
 */
@Composable
private fun IllustrationPreloadDialog(
    isPreloading: Boolean,
    progress: Float,
    completed: Int,
    total: Int,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        title = { Text("下载曲绘资源") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isPreloading) {
                    Text(
                        text = "正在下载曲绘缩略图… ($completed/$total)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
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
                } else {
                    Text(
                        text = "首次使用需要下载曲绘缩略图资源，以确保最佳显示效果。\n\n预计大小约 15MB，建议在 Wi-Fi 环境下下载。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {
            if (!isPreloading) {
                TextButton(onClick = onStartDownload) {
                    Text("开始下载")
                }
            }
        },
        dismissButton = {
            if (!isPreloading) {
                TextButton(onClick = onDismiss) {
                    Text("跳过")
                }
            }
        }
    )
}
