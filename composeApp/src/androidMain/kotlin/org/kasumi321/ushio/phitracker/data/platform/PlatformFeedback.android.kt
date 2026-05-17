package org.kasumi321.ushio.phitracker.data.platform

import android.widget.Toast

actual fun showPlatformMessage(message: String) {
    val context = AndroidPlatformContext.applicationContext ?: return
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

actual fun showPlatformAlert(title: String, message: String) {
    val activity = AndroidPlatformContext.currentActivity
    if (activity == null) {
        showPlatformMessage("$title\n$message")
        return
    }

    activity.runOnUiThread {
        PlatformAlertController.show(title, message)
    }
}
