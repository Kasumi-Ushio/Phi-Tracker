package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * Glass container for top bars and headers. Draws a progressive blur (full near
 * the status bar, fading towards the content) behind whatever header content is
 * placed inside. The content itself must use transparent backgrounds.
 */
@Composable
fun GlassTopBar(
    hazeState: HazeState,
    style: HazeStyle,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val glassBlurEnabled = rememberGlassBlurEnabled()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = style) {
                applyTopBarProgressive()
                blurEnabled = glassBlurEnabled
            }
    ) {
        content()
    }
}

/**
 * Immutable header configuration a tab hands to the home scaffold. Tabs supply
 * title, tip and action callbacks only; they never receive a ViewModel.
 */
data class HomeHeaderSpec(
    val title: String,
    val tip: String = "",
    val actions: @Composable RowScope.() -> Unit = {}
)

/** Default home top bar: title plus scrolling tip line and optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGlassTopBar(spec: HomeHeaderSpec) {
    TopAppBar(
        title = {
            Column {
                Text(spec.title)
                if (spec.tip.isNotBlank()) {
                    Text(
                        text = spec.tip,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .basicMarquee()
                    )
                }
            }
        },
        actions = spec.actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
