package org.kasumi321.ushio.phitracker.ui.b30

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class B30ExportHelpersTest {

    @Test
    fun filenameBlankNicknameUsesUnknown() {
        val instant = Instant.fromEpochSeconds(1_700_000_000)
        val name = buildB30ExportFilename("", instant, TimeZone.UTC)
        assertEquals("PhiTracker-B30-Unknown-2023-11-14-22-13-20.png", name)
    }

    @Test
    fun filenameBlankWhitespaceNicknameUsesUnknown() {
        val instant = Instant.fromEpochSeconds(1_700_000_000)
        val name = buildB30ExportFilename("   ", instant, TimeZone.UTC)
        assertEquals("PhiTracker-B30-Unknown-2023-11-14-22-13-20.png", name)
    }

    @Test
    fun filenameSanitizesInvalidChars() {
        val instant = Instant.fromEpochSeconds(1_700_000_000)
        val name = buildB30ExportFilename("test/name:with*bad?chars", instant, TimeZone.UTC)
        assertEquals("PhiTracker-B30-test_name_with_bad_chars-2023-11-14-22-13-20.png", name)
    }

    @Test
    fun filenamePreservesCJK() {
        val instant = Instant.fromEpochSeconds(1_700_000_000)
        val name = buildB30ExportFilename("玩家测试", instant, TimeZone.UTC)
        assertEquals("PhiTracker-B30-玩家测试-2023-11-14-22-13-20.png", name)
    }

    @Test
    fun filenameTruncatesAt24Chars() {
        val instant = Instant.fromEpochSeconds(1_700_000_000)
        val longName = "A".repeat(30)
        val name = buildB30ExportFilename(longName, instant, TimeZone.UTC)
        assertEquals("PhiTracker-B30-${"A".repeat(24)}-2023-11-14-22-13-20.png", name)
    }

    @Test
    fun filenameTimestampZeroPadded() {
        val instant = Instant.fromEpochSeconds(0)
        val name = buildB30ExportFilename("Test", instant, TimeZone.UTC)
        assertEquals("PhiTracker-B30-Test-1970-01-01-00-00-00.png", name)
    }

    @Test
    fun filenameTrimsNickname() {
        val instant = Instant.fromEpochSeconds(1_700_000_000)
        val name = buildB30ExportFilename("  Ushio  ", instant, TimeZone.UTC)
        assertEquals("PhiTracker-B30-Ushio-2023-11-14-22-13-20.png", name)
    }

    @Test
    fun backgroundPrecedenceCustomWins() {
        val exportData = makeExportDataWithIllustrations()
        val result = resolveBackgroundUri(
            B30BackgroundMode.Custom("file:///custom.png"),
            exportData,
            { id -> "file:///$id.png" }
        )
        assertEquals("file:///custom.png", result)
    }

    @Test
    fun backgroundPrecedenceSongBackground() {
        val exportData = makeExportDataWithIllustrations()
        val result = resolveBackgroundUri(
            B30BackgroundMode.SongBackground("song_0"),
            exportData,
            { id -> "file:///$id.png" }
        )
        assertEquals("file:///song_0.png", result)
    }

    @Test
    fun backgroundSongBackgroundUsesStandardProvider() {
        val exportData = makeExportDataWithIllustrations()
        val result = resolveBackgroundUri(
            B30BackgroundMode.SongBackground("song_0"),
            exportData,
            { id -> "https://example.com/standard/$id.png" }
        )
        assertEquals("https://example.com/standard/song_0.png", result)
    }

    @Test
    fun backgroundAutoPicksFirstAvailable() {
        val exportData = makeExportDataWithIllustrations()
        val result = resolveBackgroundUri(
            B30BackgroundMode.Auto,
            exportData,
            { id -> "file:///$id.png" }
        )
        assertEquals("file:///phi_0.png", result)
    }

    @Test
    fun backgroundEmptyReturnsNull() {
        val emptyData = B30ExportDataBuilder.build(
            b30 = emptyList(),
            displayRks = 0f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = false,
            overflowCount = 0,
            illustrationProvider = { null },
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 0,
            avatarUri = null,
            backgroundUri = null,
            dateText = ""
        )
        val result = resolveBackgroundUri(
            B30BackgroundMode.Auto,
            emptyData,
            { "https://example.com/$it.png" }
        )
        assertNull(result)
    }

    private fun makeExportDataWithIllustrations(): B30ExportData {
        val phi = listOf(makeRecord("phi_0", isPhi = true))
        val best = listOf(makeRecord("best_0", isPhi = false))
        return B30ExportDataBuilder.build(
            b30 = phi + best,
            displayRks = 15f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = false,
            overflowCount = 0,
            illustrationProvider = { null },
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 1,
            avatarUri = null,
            backgroundUri = null,
            dateText = ""
        )
    }

    private fun makeRecord(songId: String, isPhi: Boolean) =
        org.kasumi321.ushio.phitracker.domain.model.BestRecord(
            songId = songId,
            songName = "Song $songId",
            difficulty = org.kasumi321.ushio.phitracker.domain.model.Difficulty.IN,
            score = 1_000_000,
            accuracy = 100f,
            isFullCombo = true,
            chartConstant = 15f,
            rks = 15f,
            isPhi = isPhi
        )
}
