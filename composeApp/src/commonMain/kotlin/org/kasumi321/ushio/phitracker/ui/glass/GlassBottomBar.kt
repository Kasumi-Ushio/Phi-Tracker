package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Glass container for bottom bars. Uses a uniform blur (no gradient): navigation
 * icons and labels need a stable background, and the gesture area height varies
 * across devices. Both the normal and the reduced-motion bar variants must be
 * placed inside this container so the fallback never loses its themed background.
 * When blurring is disabled, the fully opaque fallbackTint restores a solid bar.
 */
@Composable
fun GlassBottomBar(
    hazeState: HazeState,
    style: HazeStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val glassBlurEnabled = rememberGlassBlurEnabled()
    val opaqueFallback = HazeTint(MaterialTheme.colorScheme.surface)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = style) {
                blurEnabled = glassBlurEnabled
                fallbackTint = opaqueFallback
            }
    ) {
        content()
    }
}
