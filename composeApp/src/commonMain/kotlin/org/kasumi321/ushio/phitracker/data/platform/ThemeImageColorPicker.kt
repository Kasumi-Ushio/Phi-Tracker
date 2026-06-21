package org.kasumi321.ushio.phitracker.data.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

data class ThemeImageColorPickResult(
    val uri: String?,
    val image: ImageBitmap
)

expect val shouldShowThemeColorSourceSetting: Boolean

@Composable
expect fun rememberThemeImageColorPicker(onResult: (ThemeImageColorPickResult?) -> Unit): () -> Unit
