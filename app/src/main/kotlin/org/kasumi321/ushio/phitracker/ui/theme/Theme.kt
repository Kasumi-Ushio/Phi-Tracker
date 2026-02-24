package org.kasumi321.ushio.phitracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun PhiTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (isAmoled && darkTheme) {
        baseColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212)
        )
    } else {
        baseColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current as? ComponentActivity
        SideEffect {
            context?.enableEdgeToEdge(
                statusBarStyle = if (darkTheme) SystemBarStyle.dark(Color.Transparent.toArgb()) 
                                 else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
                navigationBarStyle = if (darkTheme) SystemBarStyle.dark(Color.Transparent.toArgb()) 
                                     else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
