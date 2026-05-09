package org.kasumi321.ushio.phitracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
internal expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?
