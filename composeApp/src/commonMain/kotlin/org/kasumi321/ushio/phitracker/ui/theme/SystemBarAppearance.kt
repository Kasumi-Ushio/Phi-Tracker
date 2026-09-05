package org.kasumi321.ushio.phitracker.ui.theme

import androidx.compose.runtime.Composable

/**
 * Keeps the platform system bars in sync with the app's effective theme
 * rather than the system setting. On Android the activity already runs
 * edge-to-edge from `MainActivity.onCreate`; this effect only re-applies the
 * icon contrast whenever the in-app theme mode changes, so forcing a light
 * theme while the system is in dark mode (or vice versa, or AMOLED) no
 * longer leaves status bar icons with the wrong contrast. Platforms without
 * a shared-code system bar API ignore [darkTheme].
 */
@Composable
expect fun SystemBarAppearanceEffect(darkTheme: Boolean)
