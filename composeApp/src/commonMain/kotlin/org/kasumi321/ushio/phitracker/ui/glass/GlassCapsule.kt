package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * Small glass capsule for action buttons floating directly on top of images.
 * Keeps icons legible over busy artwork without a hard-coded opaque background.
 */
@Composable
fun GlassCapsule(
    hazeState: HazeState,
    style: HazeStyle,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .hazeEffect(state = hazeState, style = style),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
