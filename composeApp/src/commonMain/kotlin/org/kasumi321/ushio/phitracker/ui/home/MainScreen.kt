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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportPayload
import org.kasumi321.ushio.phitracker.ui.update.UpdateCheckState
import org.kasumi321.ushio.phitracker.ui.update.UpdateResultDialog
import org.kasumi321.ushio.phitracker.ui.utils.rememberReducedMotionEnabled
import org.koin.compose.viewmodel.koinViewModel

enum class HomeTab {
    Profile,
    B30,
    Songs,
    Tools
}

class HomeTabState(initial: HomeTab = HomeTab.Profile) {
    var selected by mutableStateOf(initial)
        private set

    fun select(tab: HomeTab) {
        selected = tab
    }

    companion object {
        val Saver = Saver<HomeTabState, Int>(
            save = { it.selected.ordinal },
            restore = { HomeTabState(HomeTab.entries[it]) }
        )
    }
}

data class BottomNavItem(
    val tab: HomeTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun MainBottomBar(
    navItems: List<BottomNavItem>,
    selectedTab: HomeTab,
    reducedMotionEnabled: Boolean,
    onTabSelected: (HomeTab) -> Unit
) {
    if (!reducedMotionEnabled) {
        NavigationBar {
            navItems.forEach { item ->
                NavigationBarItem(
                    selected = selectedTab == item.tab,
                    onClick = { onTabSelected(item.tab) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == item.tab) item.selectedIcon else item.unselectedIcon,
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
            navItems.forEach { item ->
                TextButton(onClick = { onTabSelected(item.tab) }) {
                    Text(
                        text = item.label,
                        color = if (selectedTab == item.tab) {
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
    onNavigateToB30Image: (B30ExportPayload) -> Unit,
    onNavigateToSongDetail: (String) -> Unit,
    onNavigateToSongDetailWithDifficulty: (String, org.kasumi321.ushio.phitracker.domain.model.Difficulty?) -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val tabState = rememberSaveable(saver = HomeTabState.Saver) { HomeTabState() }
    val selectedTab = tabState.selected
    val tip = remember(selectedTab) { viewModel.getRandomTip() }
    val reducedMotionEnabled = rememberReducedMotionEnabled()

    val navItems = listOf(
        BottomNavItem(HomeTab.Profile, "首页", Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem(HomeTab.B30, "B30", Icons.Filled.Star, Icons.Outlined.StarBorder),
        BottomNavItem(HomeTab.Songs, "曲目", Icons.Filled.MusicNote, Icons.Outlined.MusicNote),
        BottomNavItem(HomeTab.Tools, "工具", Icons.Filled.Build, Icons.Outlined.Build)
    )

    LaunchedEffect(state.sync.isLoggedOut) {
        if (state.sync.isLoggedOut) onLogout()
    }

    LaunchedEffect(selectedTab) {
        val tabName = when (selectedTab) {
            HomeTab.Profile -> "profile"
            HomeTab.B30 -> "b30"
            HomeTab.Songs -> "songs"
            HomeTab.Tools -> "tools"
        }
        AppLogger.event("navigation", "tab_switched", mapOf("tab" to tabName))
    }

    LaunchedEffect(state.sync.error) {
        state.sync.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Home-visible update dialog (Blocker 1 fix)
    // Composed BEFORE early returns so Available state is visible even during preload/loading
    val updateState = state.sync.updateCheckState
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

    if (state.songs.showPreloadDialog) {
        IllustrationPreloadDialog(
            isPreloading = state.songs.isPreloading,
            progress = state.songs.preloadProgress,
            completed = state.songs.preloadCompleted,
            total = state.songs.preloadTotal,
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
                onTabSelected = tabState::select
            )
        }
    ) { innerPadding ->
        when (selectedTab) {
            HomeTab.Profile -> ProfileTab(
                state = state.profile,
                displayRks = state.b30.displayRks,
                isSyncing = state.sync.isSyncing,
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
            HomeTab.B30 -> B30Tab(
                state = state.b30,
                nickname = state.profile.nickname,
                challengeModeRank = state.profile.challengeModeRank,
                onGenerateImage = {
                    onNavigateToB30Image(
                        B30ExportPayload(
                            b30 = state.b30.b30,
                            displayRks = state.b30.displayRks,
                            nickname = state.profile.nickname,
                            challengeModeRank = state.profile.challengeModeRank,
                            moneyString = state.profile.moneyString,
                            clearCounts = state.profile.clearCounts,
                            fcCount = state.profile.fcCount,
                            phiCount = state.profile.phiCount,
                            avatarUri = state.profile.avatarUri,
                            showB30Overflow = state.b30.showB30Overflow,
                            overflowCount = state.b30.overflowCount,
                            themeSettings = state.b30.themeSettings
                        )
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
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
            HomeTab.Songs -> SongsTab(
                state = state.songs,
                onSearchChange = { viewModel.searchSongs(it) },
                onToggleChapter = { viewModel.toggleChapter(it) },
                onClearChapters = { viewModel.resetFilters() },
                onDifficultySelect = { viewModel.filterByDifficulty(it) },
                onLevelRangeSelect = { min, max -> viewModel.filterByLevelRange(min, max) },
                onToggleFilterSheet = { viewModel.toggleFilterSheet(it) },
                onResetFilters = { viewModel.resetFilters() },
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                onSongClick = { songId, _ -> onNavigateToSongDetail(songId) },
                tip = tip,
                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
            )
            HomeTab.Tools -> ToolsTab(
                state = state.tools,
                defaultRks = state.b30.displayRks,
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
