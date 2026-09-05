package org.kasumi321.ushio.phitracker.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The one-time `enableEdgeToEdge()` in `MainActivity.onCreate` derives its
 * icon contrast from the system night mode and never re-runs; without this
 * effect the status bar icons would follow the system instead of the
 * in-app theme mode. `enableEdgeToEdge` keeps owning the transparent bar
 * scrims, here we only flip the contrast whenever the effective theme
 * changes.
 */
@Composable
actual fun SystemBarAppearanceEffect(darkTheme: Boolean) {
    val view = LocalView.current
    val activity = view.context as? Activity ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
