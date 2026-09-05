package org.kasumi321.ushio.phitracker.ui.theme

import androidx.compose.runtime.Composable

/**
 * iOS keeps the status bar styling in the SwiftUI hosting shell, which is
 * out of reach for the shared Compose tree, so the app theme cannot drive
 * the system bar contrast from here; Compose content already reserves the
 * status bar inset via `statusBarsPadding` in the page headers.
 */
@Composable
actual fun SystemBarAppearanceEffect(darkTheme: Boolean) = Unit
