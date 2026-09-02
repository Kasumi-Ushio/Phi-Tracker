package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

private val GlassBlurRadius = 20.dp
private const val GLASS_NOISE_FACTOR = 0.05f
private const val GLASS_TINT_ALPHA = 0.55f
private const val GLASS_FALLBACK_ALPHA = 0.9f

/**
 * App-wide glass style. Tints derive from MaterialTheme.colorScheme, so light,
 * dark, Android dynamic color and iOS image-based color all adapt automatically;
 * no hardcoded white. On Android versions where Haze falls back to a scrim, the
 * more opaque fallbackTint keeps text contrast.
 */
@Composable
fun rememberGlassHazeStyle(): HazeStyle {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    return remember(surface) {
        HazeStyle(
            backgroundColor = Color.Transparent,
            tints = listOf(HazeTint(surface.copy(alpha = GLASS_TINT_ALPHA))),
            blurRadius = GlassBlurRadius,
            noiseFactor = GLASS_NOISE_FACTOR,
            fallbackTint = HazeTint(surface.copy(alpha = GLASS_FALLBACK_ALPHA))
        )
    }
}

/** Top bar progressive blur: full blur near the status bar, fading towards the content. */
fun HazeEffectScope.applyTopBarProgressive() {
    progressive = HazeProgressive.verticalGradient(
        startIntensity = 1f,
        endIntensity = 0f,
        preferPerformance = true
    )
}
