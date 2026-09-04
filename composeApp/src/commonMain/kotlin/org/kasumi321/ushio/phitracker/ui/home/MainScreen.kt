package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.b30.B30ExportPayload
import org.kasumi321.ushio.phitracker.ui.glass.GlassBottomBar
import org.kasumi321.ushio.phitracker.ui.glass.GlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.HomeGlassScaffold
import org.kasumi321.ushio.phitracker.ui.glass.HomeGlassTopBar
import org.kasumi321.ushio.phitracker.ui.glass.HomeHeaderSpec
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
    // The surrounding GlassBottomBar supplies the blurred background; both
    // branches stay transparent so the same glass container shows through.
    if (!reducedMotionEnabled) {
        NavigationBar(containerColor = Color.Transparent) {
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
        color = Color.Transparent
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

    val generateB30Image = {
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
    }

    // B30 header collapse state: user intent is saveable UI state (never the
    // ViewModel); scrolling away from the top auto-collapses, returning to the
    // top re-expands only when the user did not explicitly collapse
    val b30ListState = rememberLazyListState()
    var b30UserCollapsed by rememberSaveable { mutableStateOf(false) }
    var b30UserExpanded by rememberSaveable { mutableStateOf(false) }
    val b30AtTop by remember {
        derivedStateOf {
            b30ListState.firstVisibleItemIndex == 0 &&
                b30ListState.firstVisibleItemScrollOffset < 48
        }
    }
    LaunchedEffect(b30AtTop) {
        if (!b30AtTop) b30UserExpanded = false
    }
    val b30HeaderExpanded = !b30UserCollapsed && (b30AtTop || b30UserExpanded)
    val toggleB30Header = {
        if (b30HeaderExpanded) {
            b30UserCollapsed = true
            b30UserExpanded = false
        } else {
            b30UserCollapsed = false
            b30UserExpanded = true
        }
    }

    // Songs header compacts on scroll into a title row with a search icon on
    // the top right; tapping the icon reopens the full search field until the
    // list returns to the top
    val songsListState = rememberLazyListState()
    val songsAtTop by remember {
        derivedStateOf {
            songsListState.firstVisibleItemIndex == 0 &&
                songsListState.firstVisibleItemScrollOffset < 48
        }
    }
    var songsSearchOpen by remember { mutableStateOf(false) }
    // Any scroll while the reopened search field is visible collapses it again
    LaunchedEffect(songsListState.isScrollInProgress) {
        if (songsListState.isScrollInProgress) songsSearchOpen = false
    }
    // Armed only by the search icon: an icon-triggered reopening focuses the
    // field (raising the IME), while automatic re-expansion when the list
    // returns to the top must not pop the keyboard
    var songsFocusOnExpand by remember { mutableStateOf(false) }

    // Profile and tools only shrink their top bar titles when scrolled away
    // from the top, no further header changes
    val profileScrollState = rememberScrollState()
    val toolsScrollState = rememberScrollState()
    val profileAtTop by remember { derivedStateOf { profileScrollState.value < 48 } }
    val toolsAtTop by remember { derivedStateOf { toolsScrollState.value < 48 } }
    val songsActiveFilterCount = remember(
        state.songs.selectedChapters,
        state.songs.selectedDifficulty,
        state.songs.minLevel,
        state.songs.maxLevel
    ) {
        var count = 0
        if (state.songs.selectedChapters.isNotEmpty()) count += state.songs.selectedChapters.size
        if (state.songs.selectedDifficulty != null) count++
        if (state.songs.minLevel > 1 || state.songs.maxLevel < 17) count++
        count
    }

    HomeGlassScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { hazeState, glassStyle ->
            // Keep the bottom edge blurred while the songs search field is
            // expanded, so the field never sits on the faded-out gradient tail
            val songsSearchExpanded = selectedTab == HomeTab.Songs &&
                (songsAtTop || songsSearchOpen)
            GlassTopBar(
                hazeState = hazeState,
                style = glassStyle,
                progressiveEndIntensity = if (songsSearchExpanded) 0.5f else 0f
            ) {
                when (selectedTab) {
                    HomeTab.B30 -> B30Header(
                        state = state.b30,
                        tip = tip,
                        expanded = b30HeaderExpanded,
                        onToggle = toggleB30Header,
                        onGenerateImage = generateB30Image
                    )
                    HomeTab.Songs -> SongsHeader(
                        songCount = state.songs.filteredSongs.size,
                        tip = tip,
                        searchQuery = state.songs.searchQuery,
                        activeFilterCount = songsActiveFilterCount,
                        compact = !songsAtTop && !songsSearchOpen,
                        focusOnExpand = songsFocusOnExpand,
                        onExpandFocusHandled = { songsFocusOnExpand = false },
                        onSearchChange = { viewModel.searchSongs(it) },
                        onSearchExpandRequest = {
                            songsFocusOnExpand = true
                            songsSearchOpen = true
                        },
                        onOpenFilter = { viewModel.toggleFilterSheet(true) }
                    )
                    HomeTab.Profile -> HomeGlassTopBar(
                        spec = HomeHeaderSpec(
                            title = "首页",
                            tip = tip,
                            compact = !profileAtTop,
                            actions = {
                                if (state.sync.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(onClick = { viewModel.refresh() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "同步")
                                    }
                                }
                                IconButton(onClick = onNavigateToSettings) {
                                    Icon(Icons.Default.Settings, contentDescription = "设置")
                                }
                            }
                        )
                    )
                    HomeTab.Tools -> HomeGlassTopBar(
                        spec = HomeHeaderSpec(title = "工具", tip = tip, compact = !toolsAtTop)
                    )
                }
            }
        },
        bottomBar = { hazeState, glassStyle ->
            GlassBottomBar(hazeState = hazeState, style = glassStyle) {
                MainBottomBar(
                    navItems = navItems,
                    selectedTab = selectedTab,
                    reducedMotionEnabled = reducedMotionEnabled,
                    onTabSelected = tabState::select
                )
            }
        }
    ) { contentPadding ->
        when (selectedTab) {
            HomeTab.Profile -> ProfileTab(
                state = state.profile,
                displayRks = state.b30.displayRks,
                onAvatarSelected = { viewModel.setAvatarUri(it) },
                onSongClick = { songId, difficulty ->
                    if (difficulty != null) {
                        onNavigateToSongDetailWithDifficulty(songId, difficulty)
                    } else {
                        onNavigateToSongDetail(songId)
                    }
                },
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                contentPadding = contentPadding,
                scrollState = profileScrollState
            )
            HomeTab.B30 -> B30Tab(
                state = state.b30,
                nickname = state.profile.nickname,
                challengeModeRank = state.profile.challengeModeRank,
                getIllustrationUrl = { viewModel.getLowIllustrationUrl(it) },
                onSongClick = { songId, difficulty ->
                    if (difficulty != null) {
                        onNavigateToSongDetailWithDifficulty(songId, difficulty)
                    } else {
                        onNavigateToSongDetail(songId)
                    }
                },
                contentPadding = contentPadding,
                listState = b30ListState,
                onRetryTagAnalysis = { viewModel.retryTagAnalysis() }
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
                contentPadding = contentPadding,
                listState = songsListState
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
                contentPadding = contentPadding,
                scrollState = toolsScrollState
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
