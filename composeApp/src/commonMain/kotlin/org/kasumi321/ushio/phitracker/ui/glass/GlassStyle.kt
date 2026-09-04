package org.kasumi321.ushio.phitracker.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * User-configurable glass behavior, provided from the app root and backed by
 * SettingsRepository. [blurStrength] ranges 0.5x..1.5x: it selects the material
 * variant (ultraThin/thin/regular/thick) and additionally scales the blur
 * radius, so each slider step is visually distinct.
 */
data class GlassSettings(
    val blurEnabled: Boolean = true,
    val blurStrength: Float = 0.75f
)

val LocalGlassSettings = compositionLocalOf { GlassSettings() }

private const val STRENGTH_ULTRA_THIN_MAX = 0.625f
private const val STRENGTH_THIN_MAX = 0.875f
private const val STRENGTH_REGULAR_MAX = 1.375f

private val GlassBlurRadius = 24.dp

/** Frosted grain level; Haze's own default is 0.15f, we triple it for a more matte look. */
private const val GLASS_NOISE_FACTOR = 0.45f

/**
 * Material-like glass tier: an opaque themed background plus a tint derived
 * from that same container color, more opaque per tier. Alpha pairs mirror the
 * well-known material tiers, picked by container luminance.
 */
private fun glassTier(container: Color, lightAlpha: Float, darkAlpha: Float): HazeStyle =
    HazeStyle(
        backgroundColor = container,
        tints = listOf(
            HazeTint(
                container.copy(
                    alpha = if (container.luminance() >= 0.5f) lightAlpha else darkAlpha
                )
            )
        ),
        blurRadius = GlassBlurRadius
    )

/**
 * App-wide glass style. The tint derives from the themed container color, so
 * the glass follows the active Monet palette: light, dark, Android dynamic
 * color, palette styles and image-based seed colors all adapt automatically.
 * [GlassSettings.blurStrength] picks the tier (ultraThin/thin/regular/thick)
 * and scales its blur radius; the noise factor is raised above the Haze
 * default for a frosted texture. When blurring is disabled (by the user or by
 * an unsupported low-API Android), Haze renders the style's fallbackTint
 * instead.
 */
@Composable
fun rememberGlassHazeStyle(): HazeStyle {
    val settings = LocalGlassSettings.current
    val container = MaterialTheme.colorScheme.surface
    val base = when {
        settings.blurStrength < STRENGTH_ULTRA_THIN_MAX -> glassTier(container, 0.35f, 0.55f)
        settings.blurStrength < STRENGTH_THIN_MAX -> glassTier(container, 0.6f, 0.65f)
        settings.blurStrength < STRENGTH_REGULAR_MAX -> glassTier(container, 0.73f, 0.8f)
        else -> glassTier(container, 0.83f, 0.9f)
    }
    return remember(base, settings.blurStrength) {
        base.copy(
            blurRadius = base.blurRadius * settings.blurStrength,
            noiseFactor = GLASS_NOISE_FACTOR
        )
    }
}

/** Whether the user allows real blurring; false renders the fallback scrim only. */
@Composable
fun rememberGlassBlurEnabled(): Boolean = LocalGlassSettings.current.blurEnabled

/**
 * Top bar progressive blur: full blur near the status bar, easing towards
 * [endIntensity] at the bottom edge. The default 0f fades fully transparent;
 * pass a higher value to keep the blur covering content near the bar's bottom.
 */
fun HazeEffectScope.applyTopBarProgressive(endIntensity: Float = 0f) {
    progressive = HazeProgressive.verticalGradient(
        startIntensity = 1f,
        endIntensity = endIntensity,
        preferPerformance = true
    )
}
