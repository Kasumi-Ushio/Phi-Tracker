package org.kasumi321.ushio.phitracker.ui.b30

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class B30ExportImageLoadTrackerTest {

    @Test
    fun captureWaitsUntilEveryCardSlotSettles() = runTest {
        val tracker = B30ExportImageLoadTracker(setOf("phi:0", "best:0"))
        tracker.onIllustrationSettled("phi:0", error = null)
        tracker.onIllustrationSettled("best:0", error = null)

        tracker.awaitAll()
    }

    @Test
    fun captureRejectsAnIncompleteImageResult() = runTest {
        val tracker = B30ExportImageLoadTracker(setOf("best:0"))
        tracker.onIllustrationSettled("best:0", IllegalStateException("decode failed"))

        assertFailsWith<IllegalStateException> { tracker.awaitAll() }
    }

    @Test
    fun slotIdsIncludeAvatarWhenAvatarUriPresent() {
        val withAvatar = exportData(avatarUri = "file:///tmp/avatar.png")
        val withoutAvatar = exportData(avatarUri = null)

        assertEquals(setOf(B30_EXPORT_AVATAR_SLOT), withAvatar.illustrationSlotIds())
        assertEquals(emptySet(), withoutAvatar.illustrationSlotIds())
    }

    private fun exportData(avatarUri: String?): B30ExportData = B30ExportData(
        nickname = "player",
        rks = 15f,
        challengeLevel = 0,
        moneyString = "",
        dateText = "",
        avatarUri = avatarUri,
        statsTable = B30StatsTable(clearCounts = emptyMap(), fcCount = 0, phiCount = 0),
        phiRecords = emptyList(),
        bestRecords = emptyList(),
        overflowRecords = emptyList(),
        backgroundUri = null
    )
}
