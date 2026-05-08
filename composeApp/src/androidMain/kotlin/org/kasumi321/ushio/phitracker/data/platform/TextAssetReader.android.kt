package org.kasumi321.ushio.phitracker.data.platform

actual fun createTextAssetReader(): TextAssetReader = AndroidTextAssetReader()

private class AndroidTextAssetReader : TextAssetReader {
    override fun readText(name: String): String {
        val context = AndroidPlatformContext.applicationContext
            ?: throw IllegalStateException(
                "AndroidPlatformContext.applicationContext not initialized. " +
                    "Call AndroidPlatformContext.initialize(context) in MainActivity.onCreate before App()."
            )
        return context.assets.open(name).bufferedReader().use { it.readText() }
    }
}
