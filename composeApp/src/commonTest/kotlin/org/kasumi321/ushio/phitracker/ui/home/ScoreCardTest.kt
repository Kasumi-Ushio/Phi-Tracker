package org.kasumi321.ushio.phitracker.ui.home

import org.kasumi321.ushio.phitracker.ui.components.ScoreRating
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreCardTest {

    @Test
    fun scoreCardFormattingRoundsLikeBeta5() {
        assertEquals("12.3457", 12.34567f.formatScoreCardRks())
        assertEquals("99.1235", 99.12345f.formatScoreCardRks())
        assertEquals("15.6", 15.56f.formatScoreCardLevel())
    }

    @Test
    fun scoreRatingMatchesPhigrosBoundaries() {
        assertEquals(ScoreRating.False, ScoreRating.fromScore(699_999, isFullCombo = false))
        assertEquals(ScoreRating.C, ScoreRating.fromScore(700_000, isFullCombo = false))
        assertEquals(ScoreRating.C, ScoreRating.fromScore(819_999, isFullCombo = false))
        assertEquals(ScoreRating.B, ScoreRating.fromScore(820_000, isFullCombo = false))
        assertEquals(ScoreRating.B, ScoreRating.fromScore(879_999, isFullCombo = false))
        assertEquals(ScoreRating.A, ScoreRating.fromScore(880_000, isFullCombo = false))
        assertEquals(ScoreRating.A, ScoreRating.fromScore(919_999, isFullCombo = false))
        assertEquals(ScoreRating.S, ScoreRating.fromScore(920_000, isFullCombo = false))
        assertEquals(ScoreRating.S, ScoreRating.fromScore(959_999, isFullCombo = false))
        assertEquals(ScoreRating.V, ScoreRating.fromScore(960_000, isFullCombo = false))
        assertEquals(ScoreRating.V, ScoreRating.fromScore(999_999, isFullCombo = false))
        assertEquals(ScoreRating.FullCombo, ScoreRating.fromScore(900_000, isFullCombo = true))
        assertEquals(ScoreRating.Phi, ScoreRating.fromScore(1_000_000, isFullCombo = true))
        assertEquals(ScoreRating.Phi, ScoreRating.fromScore(1_000_000, isFullCombo = false))
    }
}
