package org.kasumi321.ushio.phitracker.data.song

class IllustrationProvider {
    enum class Quality(val path: String) {
        STANDARD("ill"),
        LOW("illLow"),
        BLUR("illBlur")
    }

    private var baseUrl: String = DEFAULT_BASE_URL

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun getIllustrationUrl(songId: String, quality: Quality = Quality.LOW): String {
        val cleanId = songId.removeSuffix(".0")
        return "$baseUrl/${quality.path}/$cleanId.png"
    }

    fun getStandardUrl(songId: String): String = getIllustrationUrl(songId, Quality.STANDARD)
    fun getLowUrl(songId: String): String = getIllustrationUrl(songId, Quality.LOW)
    fun getBlurUrl(songId: String): String = getIllustrationUrl(songId, Quality.BLUR)

    companion object {
        private const val DEFAULT_BASE_URL =
            "https://gh-proxy.com/https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main"
    }
}
