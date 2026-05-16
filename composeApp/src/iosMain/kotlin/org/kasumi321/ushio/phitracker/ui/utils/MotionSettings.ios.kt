package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberReducedMotionEnabled(): Boolean {
    return UIAccessibilityIsReduceMotionEnabled()
}
