package org.kasumi321.ushio.phitracker.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreCardTest {

    @Test
    fun scoreCardFormattingRoundsLikeBeta5() {
        assertEquals("12.3457", 12.34567f.formatScoreCardRks())
        assertEquals("99.1235", 99.12345f.formatScoreCardRks())
        assertEquals("15.6", 15.56f.formatScoreCardLevel())
    }
}
