package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val tip = remember { viewModel.getRandomTip() }

    LaunchedEffect(state.isLoggedOut) {
        if (state.isLoggedOut) {
            onLogout()
        }
    }

    SettingsTab(
        themeMode = state.themeMode,
        showB30Overflow = state.showB30Overflow,
        overflowCount = state.overflowCount,
        onThemeModeChange = { viewModel.setThemeMode(it) },
        onShowB30OverflowChange = { viewModel.setShowB30Overflow(it) },
        onOverflowCountChange = { viewModel.setOverflowCount(it) },
        onClearHighResCache = { callback -> viewModel.clearHighResCache(onComplete = callback) },
        onRedownloadIllustrations = { viewModel.resetIllustrationDownloadAndExit() },
        onNavigateToAbout = onNavigateToAbout,
        onLogout = { viewModel.logout() },
        onNavigateBack = onNavigateBack,
        tip = tip,
        apiEnabled = state.apiEnabled,
        useApiData = state.useApiData,
        apiPlatform = state.apiPlatform,
        apiPlatformId = state.apiPlatformId,
        isApiTesting = state.isApiTesting,
        apiTestMessage = state.apiTestMessage,
        onApiEnabledChange = { viewModel.setApiEnabled(it) },
        onUseApiDataChange = { viewModel.setUseApiData(it) },
        onApiPlatformChange = { viewModel.setApiPlatform(it) },
        onApiPlatformIdChange = { viewModel.setApiPlatformId(it) },
        onApiTestConnection = { viewModel.testApiConnection() },
        modifier = androidx.compose.ui.Modifier
    )
}
