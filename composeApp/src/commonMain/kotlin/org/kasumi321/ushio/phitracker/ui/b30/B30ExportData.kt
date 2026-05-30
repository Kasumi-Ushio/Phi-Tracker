package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.ui.graphics.ImageBitmap
import org.kasumi321.ushio.phitracker.domain.model.BestRecord

/** Stats table data for the B30 export header. */
data class B30StatsTable(
    val clearCounts: Map<String, Int>,
    val fcCount: Int,
    val phiCount: Int
)

/** A single export card wrapping a record with an optional illustration URI. */
data class ExportCardData(
    val record: BestRecord,
    val illustrationUri: String?
)

/** Complete export data model, KMP-safe with no platform-specific types. */
data class B30ExportData(
    val nickname: String,
    val rks: Float,
    val challengeLevel: Int,
    val moneyString: String,
    val dateText: String,
    val avatarUri: String?,
    val statsTable: B30StatsTable,
    val phiRecords: List<ExportCardData>,
    val bestRecords: List<ExportCardData>,
    val overflowRecords: List<ExportCardData>,
    val backgroundUri: String?,
    val backgroundBitmap: ImageBitmap? = null
)

/** Pure builder that splits a B30 list into Phi / Best 27 / Overflow sections. */
object B30ExportDataBuilder {

    /**
     * Build [B30ExportData] from raw inputs.
     *
     * @param b30 sorted best records (descending by rks)
     * @param showB30Overflow whether to include overflow records
     * @param overflowCount max overflow records to include
     * @param illustrationProvider maps songId to illustration URI
     * @param clearCounts difficulty clear counts
     * @param fcCount full combo count
     * @param phiCount phi (AP) count
     * @param avatarUri optional avatar URI
     * @param backgroundUri optional background URI
     * @param dateText formatted export time string
     */
    fun build(
        b30: List<BestRecord>,
        displayRks: Float,
        nickname: String,
        challengeModeRank: Int,
        moneyString: String,
        showB30Overflow: Boolean,
        overflowCount: Int,
        illustrationProvider: (String) -> String?,
        clearCounts: Map<String, Int>,
        fcCount: Int,
        phiCount: Int,
        avatarUri: String?,
        backgroundUri: String?,
        dateText: String
    ): B30ExportData {
        val phi3 = b30.filter { it.isPhi }
        val b36 = b30.filter { !it.isPhi }
        val b27 = b36.take(27)
        val overflow = if (showB30Overflow) {
            b36.drop(27).take(overflowCount.coerceIn(1, 30))
        } else {
            emptyList()
        }

        val phiCards = phi3.map { ExportCardData(it, illustrationProvider(it.songId)) }
        val bestCards = b27.map { ExportCardData(it, illustrationProvider(it.songId)) }
        val overflowCards = overflow.map { ExportCardData(it, illustrationProvider(it.songId)) }

        return B30ExportData(
            nickname = nickname,
            rks = displayRks,
            challengeLevel = challengeModeRank,
            moneyString = moneyString,
            dateText = dateText,
            avatarUri = avatarUri,
            statsTable = B30StatsTable(
                clearCounts = clearCounts,
                fcCount = fcCount,
                phiCount = phiCount
            ),
            phiRecords = phiCards,
            bestRecords = bestCards,
            overflowRecords = overflowCards,
            backgroundUri = backgroundUri
        )
    }
}
