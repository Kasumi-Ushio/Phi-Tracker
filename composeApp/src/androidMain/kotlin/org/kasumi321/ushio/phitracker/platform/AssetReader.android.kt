package org.kasumi321.ushio.phitracker.platform

import android.content.Context

actual class AssetReader(private val context: Context) {
    actual fun readAsset(name: String): ByteArray {
        return context.assets.open(name).use { it.readBytes() }
    }
}
