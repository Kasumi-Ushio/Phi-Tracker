package org.kasumi321.ushio.phitracker.ui.b30

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
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
}
