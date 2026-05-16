package org.kasumi321.ushio.phitracker.data.platform

import android.app.AlertDialog
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
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
}
