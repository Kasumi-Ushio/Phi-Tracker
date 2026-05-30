package org.kasumi321.ushio.phitracker.ui.b30

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class B30ExportDataTest {

    private fun makeRecord(
        songIdPrefix: String = "test",
        rks: Float = 10f,
        isPhi: Boolean = false,
        index: Int = 0
    ) = BestRecord(
        songId = "${songIdPrefix}_$index",
        songName = "Song $index",
        difficulty = Difficulty.IN,
        score = 1_000_000,
        accuracy = if (isPhi) 100f else 98f,
        isFullCombo = !isPhi,
        chartConstant = 15f,
        rks = rks,
        isPhi = isPhi
    )

    private val noOpIllustrationProvider: (String) -> String? = { null }

    @Test
    fun phiRecordsAreSeparatedFromBest27() {
        val b30 = listOf(
            makeRecord(index = 0, rks = 16f, isPhi = true),
            makeRecord(index = 1, rks = 15f, isPhi = true),
            makeRecord(index = 2, rks = 14f, isPhi = false),
            makeRecord(index = 3, rks = 13f, isPhi = false)
        )

        val data = B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 15f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = false,
            overflowCount = 9,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 2,
            avatarUri = null,
            backgroundUri = null,
            dateText = "2026-05-21"
        )

        assertEquals(2, data.phiRecords.size)
        assertEquals(2, data.bestRecords.size)
        assertEquals(0, data.overflowRecords.size)
    }

    @Test
    fun best27IsCappedAt27NonPhiRecords() {
        val nonPhi = (0 until 30).map { i ->
            makeRecord(index = i, rks = (15 - i * 0.1f), isPhi = false)
        }
        val phi = listOf(makeRecord(index = 99, rks = 16f, isPhi = true))
        val b30 = phi + nonPhi

        val data = B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 15f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = true,
            overflowCount = 9,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 1,
            avatarUri = null,
            backgroundUri = null,
            dateText = "2026-05-21"
        )

        assertEquals(1, data.phiRecords.size)
        assertEquals(27, data.bestRecords.size)
    }

    @Test
    fun overflowIsDisabledWhenShowB30OverflowIsFalse() {
        val nonPhi = (0 until 30).map { i ->
            makeRecord(index = i, rks = (15 - i * 0.1f), isPhi = false)
        }
        val b30 = nonPhi

        val data = B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 15f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = false,
            overflowCount = 9,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 0,
            avatarUri = null,
            backgroundUri = null,
            dateText = "2026-05-21"
        )

        assertEquals(27, data.bestRecords.size)
        assertTrue(data.overflowRecords.isEmpty())
    }

    @Test
    fun overflowCapRespectsOverflowCount() {
        val nonPhi = (0 until 35).map { i ->
            makeRecord(index = i, rks = (15 - i * 0.1f), isPhi = false)
        }
        val b30 = nonPhi

        val data = B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 15f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = true,
            overflowCount = 5,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 0,
            avatarUri = null,
            backgroundUri = null,
            dateText = "2026-05-21"
        )

        assertEquals(27, data.bestRecords.size)
        assertEquals(5, data.overflowRecords.size)
    }

    @Test
    fun overflowCapAt30() {
        val nonPhi = (0 until 60).map { i ->
            makeRecord(index = i, rks = (15 - i * 0.1f), isPhi = false)
        }
        val b30 = nonPhi

        val data = B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 15f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = true,
            overflowCount = 30,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 0,
            avatarUri = null,
            backgroundUri = null,
            dateText = "2026-05-21"
        )

        assertEquals(27, data.bestRecords.size)
        assertEquals(30, data.overflowRecords.size)
    }

    @Test
    fun overflowCountAbove30IsClamped() {
        val nonPhi = (0 until 70).map { i ->
            makeRecord(index = i, rks = (15 - i * 0.1f), isPhi = false)
        }

        val data = B30ExportDataBuilder.build(
            b30 = nonPhi,
            displayRks = 15f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = true,
            overflowCount = 99,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 0,
            avatarUri = null,
            backgroundUri = null,
            dateText = "2026-05-21"
        )

        assertEquals(27, data.bestRecords.size)
        assertEquals(30, data.overflowRecords.size)
    }

    @Test
    fun profileFieldsAreMappedCorrectly() {
        val b30 = listOf(makeRecord(index = 0, rks = 14f, isPhi = false))

        val data = B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 14.5678f,
            nickname = "Ushio",
            challengeModeRank = 342,
            moneyString = "1.5 MiB",
            showB30Overflow = false,
            overflowCount = 9,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = mapOf("EZ" to 10, "HD" to 8, "IN" to 5, "AT" to 2),
            fcCount = 15,
            phiCount = 3,
            avatarUri = "file:///avatar.png",
            backgroundUri = null,
            dateText = "2026-05-21 12:00:00"
        )

        assertEquals("Ushio", data.nickname)
        assertEquals(14.5678f, data.rks)
        assertEquals(342, data.challengeLevel)
        assertEquals("1.5 MiB", data.moneyString)
        assertEquals("2026-05-21 12:00:00", data.dateText)
        assertEquals("file:///avatar.png", data.avatarUri)
    }

    @Test
    fun statsTableIsMappedCorrectly() {
        val b30 = listOf(makeRecord(index = 0, rks = 14f, isPhi = false))

        val data = B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 14f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = false,
            overflowCount = 9,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = mapOf("EZ" to 10, "HD" to 8, "IN" to 5, "AT" to 2),
            fcCount = 15,
            phiCount = 3,
            avatarUri = null,
            backgroundUri = null,
            dateText = ""
        )

        assertEquals(10, data.statsTable.clearCounts["EZ"])
        assertEquals(8, data.statsTable.clearCounts["HD"])
        assertEquals(5, data.statsTable.clearCounts["IN"])
        assertEquals(2, data.statsTable.clearCounts["AT"])
        assertEquals(15, data.statsTable.fcCount)
        assertEquals(3, data.statsTable.phiCount)
    }

    @Test
    fun emptyB30ProducesEmptySections() {
        val data = B30ExportDataBuilder.build(
            b30 = emptyList(),
            displayRks = 0f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = true,
            overflowCount = 9,
            illustrationProvider = noOpIllustrationProvider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 0,
            avatarUri = null,
            backgroundUri = null,
            dateText = ""
        )

        assertTrue(data.phiRecords.isEmpty())
        assertTrue(data.bestRecords.isEmpty())
        assertTrue(data.overflowRecords.isEmpty())
    }

    @Test
    fun illustrationProviderIsCalledForEachRecord() {
        val calledIds = mutableListOf<String>()
        val provider: (String) -> String? = { id ->
            calledIds.add(id)
            "https://example.com/$id.png"
        }

        val b30 = listOf(
            makeRecord(index = 0, rks = 14f, isPhi = false),
            makeRecord(index = 1, rks = 13f, isPhi = false)
        )

        B30ExportDataBuilder.build(
            b30 = b30,
            displayRks = 14f,
            nickname = "Test",
            challengeModeRank = 0,
            moneyString = "",
            showB30Overflow = false,
            overflowCount = 9,
            illustrationProvider = provider,
            clearCounts = emptyMap(),
            fcCount = 0,
            phiCount = 0,
            avatarUri = null,
            backgroundUri = null,
            dateText = ""
        )

        assertEquals(2, calledIds.size)
        assertTrue(calledIds.contains("test_0"))
        assertTrue(calledIds.contains("test_1"))
    }

    @Test
    fun resolveBackgroundUriUsesStandardProvider() {
        val b30 = listOf(makeRecord(index = 0, rks = 14f, isPhi = false))
        val data = B30ExportDataBuilder.build(
            b30 = b30, displayRks = 14f, nickname = "Test",
            challengeModeRank = 0, moneyString = "",
            showB30Overflow = false, overflowCount = 9,
            illustrationProvider = { null },
            clearCounts = emptyMap(), fcCount = 0, phiCount = 0,
            avatarUri = null, backgroundUri = null, dateText = ""
        )

        val result = resolveBackgroundUri(
            mode = B30BackgroundMode.SongBackground("test_0"),
            exportData = data,
            standardIllustrationProvider = { "https://example.com/$it.png" }
        )
        assertEquals("https://example.com/test_0.png", result)
    }

    @Test
    fun resolveBackgroundUriCustomModeReturnsUri() {
        val b30 = listOf(makeRecord(index = 0, rks = 14f, isPhi = false))
        val data = B30ExportDataBuilder.build(
            b30 = b30, displayRks = 14f, nickname = "Test",
            challengeModeRank = 0, moneyString = "",
            showB30Overflow = false, overflowCount = 9,
            illustrationProvider = { null },
            clearCounts = emptyMap(), fcCount = 0, phiCount = 0,
            avatarUri = null, backgroundUri = null, dateText = ""
        )

        val result = resolveBackgroundUri(
            mode = B30BackgroundMode.Custom("file:///album/photo.png"),
            exportData = data,
            standardIllustrationProvider = { "" }
        )
        assertEquals("file:///album/photo.png", result)
    }

    @Test
    fun resolveBackgroundUriAutoModeUsesFirstRecordStandardUrl() {
        val b30 = listOf(makeRecord(index = 0, rks = 14f, isPhi = false))
        val data = B30ExportDataBuilder.build(
            b30 = b30, displayRks = 14f, nickname = "Test",
            challengeModeRank = 0, moneyString = "",
            showB30Overflow = false, overflowCount = 9,
            illustrationProvider = { null },
            clearCounts = emptyMap(), fcCount = 0, phiCount = 0,
            avatarUri = null, backgroundUri = null, dateText = ""
        )

        val result = resolveBackgroundUri(
            mode = B30BackgroundMode.Auto,
            exportData = data,
            standardIllustrationProvider = { "https://example.com/$it.png" }
        )
        assertEquals("https://example.com/test_0.png", result)
    }
}

class B30ExportSpecTest {

    @Test
    fun contentWidthDpMatchesFormula() {
        val expected = B30ExportSpec.WIDTH_PX / B30ExportSpec.DENSITY - B30ExportSpec.PAGE_PADDING_DP * 2
        assertEquals(expected, B30ExportSpec.contentWidthDp)
    }

    @Test
    fun cardWidthDpUsesThreeColumnGrid() {
        val expected = (B30ExportSpec.contentWidthDp - B30ExportSpec.CARD_GAP_DP * 2) / 3f
        assertEquals(expected, B30ExportSpec.cardWidthDp)
    }

    @Test
    fun cardHeightDpDerivedFromAspectRatio() {
        val expected = B30ExportSpec.cardWidthDp / B30ExportSpec.CARD_ASPECT
        assertEquals(expected, B30ExportSpec.cardHeightDp)
    }

    @Test
    fun specConstantsMatchBeta5Baseline() {
        assertEquals(2400, B30ExportSpec.WIDTH_PX)
        assertEquals(2.6666667f, B30ExportSpec.DENSITY)
        assertEquals(1f, B30ExportSpec.FONT_SCALE)
        assertEquals(16f, B30ExportSpec.PAGE_PADDING_DP)
        assertEquals(9.6f, B30ExportSpec.CARD_GAP_DP)
        assertEquals(4f, B30ExportSpec.CARD_ASPECT)
        assertEquals(100f, B30ExportSpec.HEADER_HEIGHT_DP)
    }

    @Test
    fun profileStatsCardWidthsEqualCardWidth() {
        assertEquals(B30ExportSpec.cardWidthDp, B30ExportSpec.profileCardWidthDp)
        assertEquals(B30ExportSpec.cardWidthDp, B30ExportSpec.statsCardWidthDp)
    }

    @Test
    fun profileCardDimensionsAreStable() {
        assertEquals(100f, B30ExportSpec.profileCardHeightDp)
    }

    @Test
    fun gridGapsAreConsistent() {
        assertEquals(B30ExportSpec.CARD_GAP_DP, B30ExportSpec.cardHorizontalGapDp)
        assertEquals(B30ExportSpec.CARD_GAP_DP, B30ExportSpec.cardVerticalGapDp)
    }

    @Test
    fun computedDimensionsArePositive() {
        assertTrue(B30ExportSpec.contentWidthDp > 0f)
        assertTrue(B30ExportSpec.cardWidthDp > 0f)
        assertTrue(B30ExportSpec.cardHeightDp > 0f)
    }

    @Test
    fun cardWidthIsLessThanContentWidth() {
        assertTrue(B30ExportSpec.cardWidthDp < B30ExportSpec.contentWidthDp)
    }

    @Test
    fun threeCardsPlusGapsFitContentWidth() {
        val totalNeeded = B30ExportSpec.cardWidthDp * 3 + B30ExportSpec.CARD_GAP_DP * 2
        assertEquals(B30ExportSpec.contentWidthDp, totalNeeded, 0.01f)
    }

    @Test
    fun sectionFooterLayoutConstantsMatchBeta5Baseline() {
        assertEquals(30f, B30ExportSpec.sectionTitleHeightDp)
        assertEquals(30f, B30ExportSpec.footerHeightDp)
        assertEquals(11.25f, B30ExportSpec.footerTextOffsetDp)
    }

    @Test
    fun sectionFooterLayoutConstantsArePositive() {
        assertTrue(B30ExportSpec.sectionTitleHeightDp > 0f)
        assertTrue(B30ExportSpec.footerHeightDp > 0f)
        assertTrue(B30ExportSpec.footerTextOffsetDp > 0f)
    }

    @Test
    fun sectionFooterPixelConversionMatchesCurrentRender() {
        // When converted to px at spec density, the dp values must produce
        // the pixel values used by the current iOS Skia render fallback.
        // This prevents accidental visual drift in platform renderers.
        val d = B30ExportSpec.DENSITY
        val eps = 0.05f // epsilon for floating-point comparison at px scale
        assertEquals(80f, B30ExportSpec.sectionTitleHeightDp * d, eps)
        assertEquals(80f, B30ExportSpec.footerHeightDp * d, eps)
        assertEquals(30f, B30ExportSpec.footerTextOffsetDp * d, eps)
    }
}
