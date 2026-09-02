package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * Glass container for bottom bars. Uses a uniform blur (no gradient): navigation
 * icons and labels need a stable background, and the gesture area height varies
 * across devices. Both the normal and the reduced-motion bar variants must be
 * placed inside this container so the fallback never loses its themed background.
 */
@Composable
fun GlassBottomBar(
    hazeState: HazeState,
    style: HazeStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = style)
    ) {
        content()
    }
}
