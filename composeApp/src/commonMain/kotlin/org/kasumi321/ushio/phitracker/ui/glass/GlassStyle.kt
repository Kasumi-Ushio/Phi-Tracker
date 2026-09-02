package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
 * User-configurable glass behavior, provided from the app root and backed by
 * SettingsRepository. [blurStrength] scales the blur radius (0.5x..2.0x).
 */
data class GlassSettings(
    val blurEnabled: Boolean = true,
    val blurStrength: Float = 1f
)

val LocalGlassSettings = compositionLocalOf { GlassSettings() }

/**
 * App-wide glass style. Tints derive from MaterialTheme.colorScheme, so light,
 * dark, Android dynamic color and iOS image-based color all adapt automatically;
 * no hardcoded white. The blur radius scales with the user's strength setting.
 * When blurring is disabled (by the user or by an unsupported low-API Android),
 * the more opaque fallbackTint keeps text contrast.
 */
@Composable
fun rememberGlassHazeStyle(): HazeStyle {
    val settings = LocalGlassSettings.current
    val surface = MaterialTheme.colorScheme.surfaceContainer
    return remember(surface, settings.blurStrength) {
        HazeStyle(
            backgroundColor = Color.Transparent,
            tints = listOf(HazeTint(surface.copy(alpha = GLASS_TINT_ALPHA))),
            blurRadius = GlassBlurRadius * settings.blurStrength,
            noiseFactor = GLASS_NOISE_FACTOR,
            fallbackTint = HazeTint(surface.copy(alpha = GLASS_FALLBACK_ALPHA))
        )
    }
}

/** Whether the user allows real blurring; false renders the fallback scrim only. */
@Composable
fun rememberGlassBlurEnabled(): Boolean = LocalGlassSettings.current.blurEnabled

/** Top bar progressive blur: full blur near the status bar, fading towards the content. */
fun HazeEffectScope.applyTopBarProgressive() {
    progressive = HazeProgressive.verticalGradient(
        startIntensity = 1f,
        endIntensity = 0f,
        preferPerformance = true
    )
}
