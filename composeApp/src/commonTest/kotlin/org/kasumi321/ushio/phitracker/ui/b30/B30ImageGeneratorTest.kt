package org.kasumi321.ushio.phitracker.ui.b30

import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals

class B30ImageGeneratorTest {
    @Test
    fun beta1ExportDimensionsArePreserved() {
        assertEquals(360, B30ImageSpec.CARD_WIDTH)
        assertEquals(160, B30ImageSpec.CARD_HEIGHT)
        assertEquals(5, B30ImageSpec.COLUMNS)
        assertEquals(6, B30ImageSpec.ROWS)
        assertEquals(16, B30ImageSpec.PADDING)
        assertEquals(200, B30ImageSpec.HEADER_HEIGHT)
        assertEquals(60, B30ImageSpec.FOOTER_HEIGHT)
        assertEquals(12, B30ImageSpec.GAP)
        assertEquals(1880, B30ImageSpec.IMAGE_WIDTH)
        assertEquals(1312, B30ImageSpec.IMAGE_HEIGHT)
    }

    @Test
    fun beta1TextFormattingIsPreserved() {
        assertEquals("12.3457", B30ImageSpec.formatRks(12.34567f))
        assertEquals("99.1235%", B30ImageSpec.formatAccuracy(99.12345f))
        assertEquals("15.6", B30ImageSpec.formatChartConstant(15.56f))
        assertEquals("1,000,000", B30ImageSpec.formatScore(1_000_000))
    }

    @Test
    fun beta1DifficultyLabelsAndColorsArePreserved() {
        assertEquals("EZ", B30ImageSpec.difficultyLabel(Difficulty.EZ))
        assertEquals("HD", B30ImageSpec.difficultyLabel(Difficulty.HD))
        assertEquals("IN", B30ImageSpec.difficultyLabel(Difficulty.IN))
        assertEquals("AT", B30ImageSpec.difficultyLabel(Difficulty.AT))

        assertEquals(0xFF70D866.toInt(), B30ImageSpec.difficultyColor(Difficulty.EZ))
        assertEquals(0xFF58B4E3.toInt(), B30ImageSpec.difficultyColor(Difficulty.HD))
        assertEquals(0xFFE34D4D.toInt(), B30ImageSpec.difficultyColor(Difficulty.IN))
        assertEquals(0xFFA855F7.toInt(), B30ImageSpec.difficultyColor(Difficulty.AT))
    }

    @Test
    fun blankNicknameFallsBackToBeta1Default() {
        assertEquals("Phigros Player", B30ImageSpec.displayNickname(""))
        assertEquals("Phigros Player", B30ImageSpec.displayNickname("   "))
        assertEquals("Ushio", B30ImageSpec.displayNickname("Ushio"))
    }

}
