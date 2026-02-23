package org.kasumi321.ushio.phitracker.data.song

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 曲绘资源 URL 构建器
 *
 * 曲绘来源: https://github.com/Catrong/phi-plugin-ill
 * 目录结构:
 *   ill/{songId}.png      — 标准画质
 *   illLow/{songId}.png   — 低画质 (缩略图推荐)
 *   illBlur/{songId}.png  — 模糊
 */
@Singleton
class IllustrationProvider @Inject constructor() {

    enum class Quality(val path: String) {
        STANDARD("ill"),
        LOW("illLow"),
        BLUR("illBlur")
    }

    companion object {
        private const val DEFAULT_BASE_URL =
            "https://raw.githubusercontent.com/Catrong/phi-plugin-ill/main"
    }

    private var baseUrl: String = DEFAULT_BASE_URL

    /**
     * 设置自定义 base URL (镜像/代理)
     */
    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /**
     * 获取曲绘 URL
     *
     * @param songId 曲目 ID (带或不带 ".0" 后缀均可)
     * @param quality 画质级别
     */
    fun getIllustrationUrl(songId: String, quality: Quality = Quality.LOW): String {
        // phi-plugin-ill 使用不带 ".0" 后缀的文件名
        val cleanId = songId.removeSuffix(".0")
        return "$baseUrl/${quality.path}/$cleanId.png"
    }

    /**
     * 获取标准画质 URL
     */
    fun getStandardUrl(songId: String): String =
        getIllustrationUrl(songId, Quality.STANDARD)

    /**
     * 获取低画质 URL (缩略图用)
     */
    fun getLowUrl(songId: String): String =
        getIllustrationUrl(songId, Quality.LOW)

    /**
     * 获取模糊版 URL (背景用)
     */
    fun getBlurUrl(songId: String): String =
        getIllustrationUrl(songId, Quality.BLUR)
}
