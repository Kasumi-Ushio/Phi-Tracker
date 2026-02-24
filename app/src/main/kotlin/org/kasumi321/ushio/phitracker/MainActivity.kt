package org.kasumi321.ushio.phitracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import org.kasumi321.ushio.phitracker.domain.repository.SettingsRepository
import org.kasumi321.ushio.phitracker.ui.PhiTrackerNavHost
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = 0)

            val darkTheme = when (themeMode) {
                1 -> false
                2, 3 -> true
                else -> isSystemInDarkTheme()
            }
            val isAmoled = themeMode == 3

            PhiTrackerTheme(
                darkTheme = darkTheme,
                isAmoled = isAmoled
            ) {
                PhiTrackerNavHost()
            }
        }
    }
}
