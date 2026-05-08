package org.kasumi321.ushio.phitracker.data.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.stringWithContentsOfFile

actual fun createTextAssetReader(): TextAssetReader = IosTextAssetReader()

private class IosTextAssetReader : TextAssetReader {
    override fun readText(name: String): String {
        val path = NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), name.substringAfterLast('.'))
            ?: error("Asset not found: $name")
        return NSString.stringWithContentsOfFile(path) as String
    }
}
