package org.kasumi321.ushio.phitracker.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.kasumi321.ushio.phitracker.ui.home.HomeViewModel

/**
 * 设置页面（全屏版本）
 * 设置从底部 Tab 迁移为独立页面，由首页右上角齿轮按钮触发导航
 */
@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val tip = remember { viewModel.getRandomTip() }

    SettingsTab(
        themeMode = state.themeMode,
        showB30Overflow = state.showB30Overflow,
        overflowCount = state.overflowCount,
        onThemeModeChange = { viewModel.setThemeMode(it) },
        onShowB30OverflowChange = { viewModel.setShowB30Overflow(it) },
        onOverflowCountChange = { viewModel.setOverflowCount(it) },
        onClearHighResCache = { viewModel.clearHighResCache() },
        onRedownloadIllustrations = { viewModel.resetIllustrationDownloadAndExit() },
        isUpdatingData = state.isUpdatingData,
        updateDataProgress = state.updateDataProgress,
        updateDataTotal = state.updateDataTotal,
        updateDataFileName = state.updateDataFileName,
        updateDataError = state.updateDataError,
        onUpdateSongData = { viewModel.updateSongData() },
        onDismissUpdateError = { viewModel.dismissUpdateDataError() },
        onNavigateToAbout = onNavigateToAbout,
        onLogout = { viewModel.logout(onLogout) },
        includePreRelease = state.includePreRelease,
        updateCheckState = state.updateCheckState,
        onCheckForUpdate = { viewModel.checkForUpdate() },
        onIncludePreReleaseChange = { viewModel.setIncludePreRelease(it) },
        onDismissUpdateResult = { viewModel.dismissUpdateResult() },
        apiEnabled = state.apiEnabled,
        useApiData = state.useApiData,
        apiPlatform = state.apiPlatform,
        apiPlatformId = state.apiPlatformId,
        isApiTesting = state.isApiTesting,
        apiTestMessage = state.apiTestMessage,
        onApiEnabledChange = { enabled ->
            if (enabled) viewModel.enableApi() else viewModel.disableApi()
        },
        onUseApiDataChange = { viewModel.setUseApiData(it) },
        onApiPlatformChange = { viewModel.setApiPlatform(it) },
        onApiPlatformIdChange = { viewModel.setApiPlatformId(it) },
        onApiTestConnection = { viewModel.testApiConnection() },
        tip = tip,
        onNavigateBack = onNavigateBack
    )
}
