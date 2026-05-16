package org.kasumi321.ushio.phitracker.data.platform

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

actual fun hasCrashNotificationPermission(): Boolean {
    val context = AndroidPlatformContext.applicationContext ?: return false
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

actual fun requestCrashNotificationPermission(onResult: (Boolean) -> Unit) {
    val activity = AndroidPlatformContext.currentActivity
    if (activity == null) {
        onResult(false)
        return
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            NotificationPermissionRequester.request(activity, onResult)
        } else {
            onResult(true)
        }
    } else {
        onResult(true)
    }
}

internal object NotificationPermissionRequester {
    private var callback: ((Boolean) -> Unit)? = null

    fun request(activity: Activity, onResult: (Boolean) -> Unit) {
        callback = onResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE
            )
        }
    }

    fun handleResult(granted: Boolean) {
        callback?.invoke(granted)
        callback = null
    }

    const val REQUEST_CODE = 1001
}
