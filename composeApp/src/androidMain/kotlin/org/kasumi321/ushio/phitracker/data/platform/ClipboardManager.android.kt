package org.kasumi321.ushio.phitracker.data.platform

import android.content.ClipData
import android.content.Context

actual fun copyToClipboard(label: String, text: String) {
    val context = AndroidPlatformContext.applicationContext ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
