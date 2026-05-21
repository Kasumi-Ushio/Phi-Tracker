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
}
