package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import org.koin.compose.viewmodel.koinViewModel

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun MainBottomBar(
    navItems: List<BottomNavItem>,
    selectedTab: Int,
    reducedMotionEnabled: Boolean,
    onTabSelected: (Int) -> Unit
) {
    if (!reducedMotionEnabled) {
        NavigationBar {
            navItems.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
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
        return
    }

    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            navItems.forEachIndexed { index, item ->
                TextButton(onClick = { onTabSelected(index) }) {
                    Text(
                        text = item.label,
                        color = if (selectedTab == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToB30Image: (
        b30: List<org.kasumi321.ushio.phitracker.domain.model.BestRecord>,
        displayRks: Float,
        nickname: String,
        challengeModeRank: Int,
        moneyString: String,
        clearCounts: Map<String, Int>,
        fcCount: Int,
        phiCount: Int,
        avatarUri: String?,
        showB30Overflow: Boolean,
        overflowCount: Int,
        themeSettings: PhiTrackerThemeSettings
    ) -> Unit,
    onNavigateToSongDetail: (String) -> Unit,
    onNavigateToSongDetailWithDifficulty: (String, org.kasumi321.ushio.phitracker.domain.model.Difficulty?) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tip = remember(selectedTab) { viewModel.getRandomTip() }
    val reducedMotionEnabled = rememberReducedMotionEnabled()

    val navItems = listOf(
        BottomNavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("B30", Icons.Filled.Star, Icons.Outlined.StarBorder),
        BottomNavItem("曲目", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
        BottomNavItem("工具", Icons.Filled.Build, Icons.Outlined.Build)
    )

    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) onLogout()
    }

    LaunchedEffect(selectedTab) {
        val tabName = when (selectedTab) {
            0 -> "profile"
            1 -> "b30"
            2 -> "songs"
            3 -> "tools"
            else -> "unknown"
        }
        AppLogger.event("navigation", "tab_switched", mapOf("tab" to tabName))
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Home-visible update dialog (Blocker 1 fix)
    // Composed BEFORE early returns so Available state is visible even during preload/loading
    val updateState = state.updateCheckState
    if (updateState is UpdateCheckState.Available) {
        UpdateResultDialog(
            version = updateState.version,
            body = updateState.body,
            htmlUrl = updateState.htmlUrl,
            onDismiss = { viewModel.dismissUpdateResult() },
            onDownload = { uriHandler ->
                viewModel.dismissUpdateResult()
                uriHandler.openUri(updateState.htmlUrl)
            }
        )
    }

    if (state.showPreloadDialog) {
        IllustrationPreloadDialog(
            isPreloading = state.isPreloading,
            progress = state.preloadProgress,
            completed = state.preloadCompleted,
            total = state.preloadTotal,
            onStartDownload = { viewModel.startPreloadIllustrations() },
            onDismiss = { viewModel.dismissPreload() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MainBottomBar(
                navItems = navItems,
                selectedTab = selectedTab,
                reducedMotionEnabled = reducedMotionEnabled,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> ProfileTab(
                nickname = state.nickname,
                displayRks = state.displayRks,
                challengeModeRank = state.challengeModeRank,
                moneyString = state.moneyString,
                clearCounts = state.clearCounts,
                fcCount = state.fcCount,
                phiCount = state.phiCount,
                avatarUri = state.avatarUri,
                lastSyncTime = state.lastSyncTime,
                recentSyncedRecords = state.recentSyncedRecords,
                isSyncing = state.isSyncing,
                onRefresh = { viewModel.refresh() },
                onAvatarSelected = { viewModel.setAvatarUri(it) },
                onNavigateToSettings = onNavigateToSettings,
                onSongClick = { songId, difficulty ->
                    if (difficulty != null) {
                        onNavigateToSongDetailWithDifficulty(songId, difficulty)
                    } else {
                        onNavigateToSongDetail(songId)
                    }
                },
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
            1 -> B30Tab(
                b30 = state.b30,
                displayRks = state.displayRks,
                nickname = state.nickname,
                challengeModeRank = state.challengeModeRank,
                onGenerateImage = {
                    onNavigateToB30Image(
                        state.b30, state.displayRks, state.nickname,
                        state.challengeModeRank, state.moneyString,
                        state.clearCounts, state.fcCount, state.phiCount,
                        state.avatarUri, state.showB30Overflow, state.overflowCount,
                        state.themeSettings
                    )
                },
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                onSongClick = { songId, difficulty ->
                    if (difficulty != null) {
                        onNavigateToSongDetailWithDifficulty(songId, difficulty)
                    } else {
                        onNavigateToSongDetail(songId)
                    }
                },
                showB30Overflow = state.showB30Overflow,
                overflowCount = state.overflowCount,
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
            2 -> SongsTab(
                songs = state.filteredSongs,
                searchQuery = state.searchQuery,
                onSearchChange = { viewModel.searchSongs(it) },
                availableChapters = state.availableChapters,
                selectedChapters = state.selectedChapters,
                onToggleChapter = { viewModel.toggleChapter(it) },
                onClearChapters = { viewModel.resetFilters() },
                selectedDifficulty = state.selectedDifficulty,
                onDifficultySelect = { viewModel.filterByDifficulty(it) },
                minLevel = state.minLevel,
                maxLevel = state.maxLevel,
                onLevelRangeSelect = { min, max -> viewModel.filterByLevelRange(min, max) },
                showFilterSheet = state.showFilterSheet,
                onToggleFilterSheet = { viewModel.toggleFilterSheet(it) },
                onResetFilters = { viewModel.resetFilters() },
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                onSongClick = { songId, _ -> onNavigateToSongDetail(songId) },
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
            3 -> ToolsTab(
                syncSnapshots = viewModel.getToolSnapshots(),
                sessionToken = state.sessionToken,
                apiEnabled = state.apiEnabled,
                useApiData = state.useApiData,
                defaultRks = state.displayRks,
                apiRankByUser = state.apiRankByUser,
                apiRankByPosition = state.apiRankByPosition,
                apiRksRankResult = state.apiRksRankResult,
                suggestTargetMode = state.suggestTargetMode,
                suggestTargetInput = state.suggestTargetInput,
                suggestTargetError = state.suggestTargetError,
                suggestItems = state.suggestItems,
                onSuggestTargetModeChange = { viewModel.setSuggestTargetMode(it) },
                onSuggestTargetInputChange = { viewModel.setSuggestTargetInput(it) },
                onFetchRankByUser = { viewModel.fetchApiRankByUser() },
                onFetchRankByPosition = { viewModel.fetchApiRankByPosition(it) },
                onFetchRksRank = { viewModel.fetchApiRksRankForValue(it) },
                onSuggestionClick = { songId, difficulty ->
                    onNavigateToSongDetailWithDifficulty(songId, difficulty)
                },
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
        }
    }
}

@Composable
internal fun UpdateResultDialog(
    version: String,
    body: String,
    htmlUrl: String,
    onDismiss: () -> Unit,
    onDownload: (androidx.compose.ui.platform.UriHandler) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本") },
        text = {
            Column {
                Text("最新版本: $version")
                if (body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 10
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDownload(uriHandler) }) {
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        }
    )
}

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
        onDismissRequest = { },
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
                        text = "首次使用需要下载曲绘缩略图资源包以正常显示曲目封面。\n\n预计约 60 MB，推荐在 Wi-Fi 下完成。",
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
