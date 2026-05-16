package org.kasumi321.ushio.phitracker.ui.utils

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    var reducedMotionEnabled by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }

    DisposableEffect(contentResolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotionEnabled = !ValueAnimator.areAnimatorsEnabled()
            }
        }

        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )

        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return reducedMotionEnabled
}
