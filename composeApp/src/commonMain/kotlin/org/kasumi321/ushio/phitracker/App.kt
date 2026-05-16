package org.kasumi321.ushio.phitracker

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import org.kasumi321.ushio.phitracker.data.logging.AppLogger
import org.kasumi321.ushio.phitracker.data.platform.ConfigureCoilImageLoader
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.ui.PhiTrackerNavHost
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    ConfigureCoilImageLoader()

    val settingsRepository: SettingsRepository = koinInject()
    val themeMode by settingsRepository.themeMode.collectAsState(initial = 0)

    val darkTheme = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemInDarkTheme()
    }
    val isAmoled = themeMode == 3

    PhiTrackerTheme(darkTheme = darkTheme, isAmoled = isAmoled) {
        PhiTrackerNavHost()
    }
}

internal val appLogger: AppLogger get() = AppLogger
