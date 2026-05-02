package org.kasumi321.ushio.phitracker.platform

expect class AssetReader {
    fun readAsset(name: String): ByteArray
}
