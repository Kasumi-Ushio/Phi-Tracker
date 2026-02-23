package org.kasumi321.ushio.phitracker.ui.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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
                modifier = Modifier.padding(innerPadding)
            )
            1 -> SongsTab(
                songs = state.filteredSongs,
                searchQuery = state.searchQuery,
                onSearchChange = { viewModel.searchSongs(it) },
                getIllustrationUrl = { viewModel.getIllustrationUrl(it) },
                modifier = Modifier.padding(innerPadding)
            )
            2 -> SettingsTab(
                onLogout = { viewModel.logout() },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
