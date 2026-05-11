package org.kasumi321.ushio.phitracker.data.song

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IllustrationProviderTest {

    private val provider = IllustrationProvider().apply {
        setBaseUrl("https://example.test")
    }

    @Test
    fun getLowUrlContainsIllLow() {
        val url = provider.getLowUrl("song-a")
        assertTrue(url.contains("/illLow/"), "Low URL must contain /illLow/")
    }

    @Test
    fun getStandardUrlContainsIll() {
        val url = provider.getStandardUrl("song-a")
        assertTrue(url.contains("/ill/"), "Standard URL must contain /ill/")
    }

    @Test
    fun standardUrlDoesNotContainIllLow() {
        val url = provider.getStandardUrl("song-a")
        assertTrue(!url.contains("/illLow/"), "Standard URL must not contain /illLow/")
    }

    @Test
    fun lowUrlDoesNotContainIllWithoutLow() {
        val url = provider.getLowUrl("song-a")
        assertEquals("https://example.test/illLow/song-a.png", url)
    }

    @Test
    fun removesZeroSuffixFromSongId() {
        val url = provider.getLowUrl("song-a.0")
        assertEquals("https://example.test/illLow/song-a.png", url)
    }

    @Test
    fun standardUrlRemovesZeroSuffix() {
        val url = provider.getStandardUrl("song-b.0")
        assertEquals("https://example.test/ill/song-b.png", url)
    }

    @Test
    fun blurUrlContainsIllBlur() {
        val url = provider.getBlurUrl("song-c")
        assertTrue(url.contains("/illBlur/"), "Blur URL must contain /illBlur/")
    }

    @Test
    fun defaultQualityIsLow() {
        val url = provider.getIllustrationUrl("song-d")
        assertTrue(url.contains("/illLow/"), "Default quality=getIllustrationUrl should return /illLow/")
    }
}
