package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import org.koin.compose.viewmodel.koinViewModel

data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNavigateToB30Image: (b30: List<org.kasumi321.ushio.phitracker.domain.model.BestRecord>, displayRks: Float, nickname: String) -> Unit,
    onNavigateToSongDetail: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
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

    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) onLogout()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
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

    if (!state.illustrationReady) {
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
                onGenerateImage = { onNavigateToB30Image(state.b30, state.displayRks, state.nickname) },
                getIllustrationUrl = { viewModel.getIllustrationUrl(it) },
                onSongClick = onNavigateToSongDetail,
                showB30Overflow = state.showB30Overflow,
                overflowCount = state.overflowCount,
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
            2 -> SettingsTabPlaceholder(
                onLogout = { viewModel.logout() },
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
                        text = "首次使用需要下载曲绘缩略图资源，以确保最佳显示效果。\n\n预计大小约 60 MB，建议在 Wi-Fi 环境下下载。",
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

@Composable
private fun SettingsTabPlaceholder(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Phase 6: 设置页待实现",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onLogout) {
                Text("退出登录")
            }
        }
    }
}
