package org.kasumi321.ushio.phitracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import phitracker.composeapp.generated.resources.Res
import phitracker.composeapp.generated.resources.roboto_bold

/**
 * Roboto Bold, bundled from the googlefonts/roboto v2.138 release (Apache-2.0),
 * used exclusively for the phi rating mark so the glyph renders identically on
 * every device instead of depending on the system sans-serif fallback chain.
 */
val phiFontFamily: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.roboto_bold, weight = FontWeight.Bold))
