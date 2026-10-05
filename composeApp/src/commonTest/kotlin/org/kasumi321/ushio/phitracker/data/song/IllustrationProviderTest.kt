package org.kasumi321.ushio.phitracker.data.song

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

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

    private val perDifficultySong = IllustrationProvider.PER_DIFFICULTY_SONGS.keys.single()

    @Test
    fun knownSongUsesPerDifficultyLowUrl() {
        val url = provider.getLowUrl("$perDifficultySong.0", Difficulty.IN)
        assertEquals("https://example.test/illLow/IN/$perDifficultySong.png", url)
    }

    @Test
    fun knownSongUsesPerDifficultyStandardUrl() {
        val url = provider.getStandardUrl(perDifficultySong, Difficulty.AT)
        assertEquals("https://example.test/ill/AT/$perDifficultySong.png", url)
    }

    @Test
    fun knownSongUsesPerDifficultyBlurUrl() {
        val url = provider.getBlurUrl(perDifficultySong, Difficulty.EZ)
        assertEquals("https://example.test/illBlur/EZ/$perDifficultySong.png", url)
    }

    @Test
    fun knownSongWithoutDifficultyUsesHighestVariantUrl() {
        val url = provider.getLowUrl("$perDifficultySong.0")
        assertEquals("https://example.test/illLow/AT/$perDifficultySong.png", url)
    }

    @Test
    fun knownSongWithoutDifficultyUsesHighestVariantCacheSlot() {
        assertEquals("$perDifficultySong.0_AT", provider.cacheKey("$perDifficultySong.0", null))
        assertEquals("$perDifficultySong.0_HD", provider.cacheKey("$perDifficultySong.0", Difficulty.HD))
    }

    @Test
    fun variantDifficultiesCoverEveryDifficultyUpToTheHighest() {
        assertEquals(
            listOf(Difficulty.EZ, Difficulty.HD, Difficulty.IN, Difficulty.AT),
            provider.variantDifficulties("$perDifficultySong.0")
        )
        assertEquals(emptyList(), provider.variantDifficulties("song-a"))
    }

    @Test
    fun unknownSongIgnoresDifficultyAndKeepsFlatUrl() {
        val url = provider.getStandardUrl("song-e.0", Difficulty.HD)
        assertEquals("https://example.test/ill/song-e.png", url)
    }
}
