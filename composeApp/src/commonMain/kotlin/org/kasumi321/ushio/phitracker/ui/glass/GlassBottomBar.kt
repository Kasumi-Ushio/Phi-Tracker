package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Glass container for bottom bars. Uses a uniform blur (no gradient): navigation
 * icons and labels need a stable background, and the gesture area height varies
 * across devices. Both the normal and the reduced-motion bar variants must be
 * placed inside this container so the fallback never loses its themed background.
 * When blurring is disabled, the fully opaque fallback color effect restores a solid bar.
 */
@Composable
fun GlassBottomBar(
    hazeState: HazeState,
    style: HazeBlurStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = style.then {
                    fallbackColorEffect(HazeColorEffect.tint(surface))
                }
            )
    ) {
        content()
    }
}
