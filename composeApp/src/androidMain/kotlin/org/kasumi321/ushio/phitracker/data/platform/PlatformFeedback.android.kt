package org.kasumi321.ushio.phitracker.data.platform

import android.widget.Toast

actual fun showPlatformMessage(message: String) {
    val context = AndroidPlatformContext.applicationContext ?: return
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
