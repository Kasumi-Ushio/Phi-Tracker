package org.kasumi321.ushio.phitracker.data.platform

interface TextAssetReader {
    fun readText(name: String): String
}

expect fun createTextAssetReader(): TextAssetReader
