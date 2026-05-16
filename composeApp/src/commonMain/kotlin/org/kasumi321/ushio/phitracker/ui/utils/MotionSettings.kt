package org.kasumi321.ushio.phitracker.ui.utils

import androidx.compose.runtime.Composable

/**
 * Returns whether the system has reduced motion enabled.
 *
 * On Android, this checks `ValueAnimator.areAnimatorsEnabled()` and listens
 * to `Settings.Global.ANIMATOR_DURATION_SCALE` changes.
 *
 * On iOS, this checks `UIAccessibilityIsReduceMotionEnabled()`.
 */
@Composable
expect fun rememberReducedMotionEnabled(): Boolean
