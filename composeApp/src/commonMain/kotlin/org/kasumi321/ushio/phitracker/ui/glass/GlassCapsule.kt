package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Small glass capsule for action buttons floating directly on top of images.
 * Keeps icons legible over busy artwork without a hard-coded opaque background.
 */
@Composable
fun GlassCapsule(
    hazeState: HazeState,
    style: HazeBlurStyle,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .hazeBlur(input = HazeInput.Sources(hazeState), style = style),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
