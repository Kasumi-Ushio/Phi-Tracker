package org.kasumi321.ushio.phitracker.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun systemSeedColor(): Color? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return Color(LocalContext.current.getColor(android.R.color.system_accent1_500))
}
