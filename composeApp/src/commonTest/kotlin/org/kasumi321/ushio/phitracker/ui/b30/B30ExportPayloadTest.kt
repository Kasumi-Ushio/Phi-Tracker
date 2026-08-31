package org.kasumi321.ushio.phitracker.ui.b30

import kotlin.test.Test
import kotlin.test.assertEquals
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.theme.PhiTrackerThemeSettings

class B30ExportPayloadTest {
    @Test
    fun copiesMutableCollectionsAtCreation() {
        val records = mutableListOf(record("first"))
        val clearCounts = mutableMapOf("IN" to 4)

        val payload = payload(records, clearCounts)
        records += record("later")
        clearCounts["IN"] = 99

        assertEquals(listOf(record("first")), payload.b30)
        assertEquals(mapOf("IN" to 4), payload.clearCounts)
    }

    private fun payload(
        records: List<BestRecord> = listOf(record("first")),
        clearCounts: Map<String, Int> = mapOf("IN" to 4)
    ) = B30ExportPayload(
        b30 = records,
        displayRks = 15.4f,
        nickname = "Player",
        challengeModeRank = 48,
        moneyString = "1 2 3 4 5",
        clearCounts = clearCounts,
        fcCount = 7,
        phiCount = 2,
        avatarUri = "avatar://player",
        showB30Overflow = true,
        overflowCount = 9,
        themeSettings = PhiTrackerThemeSettings()
    )

    private fun record(songId: String) = BestRecord(
        songId = songId,
        songName = "Song $songId",
        difficulty = Difficulty.IN,
        score = 987_654,
        accuracy = 98.76f,
        isFullCombo = true,
        chartConstant = 15.2f,
        rks = 14.4f
    )
}
