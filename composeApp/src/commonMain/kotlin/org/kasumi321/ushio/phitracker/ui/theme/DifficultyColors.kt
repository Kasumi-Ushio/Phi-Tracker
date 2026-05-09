package org.kasumi321.ushio.phitracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

/**
 * Phigros 难度颜色
 */
object DifficultyColors {
    val EZ = Color(0xFF70D866)
    val HD = Color(0xFF58B4E3)
    val IN = Color(0xFFE34D4D)
    val AT = Color(0xFFA855F7)

    fun forDifficulty(difficulty: Difficulty): Color = when (difficulty) {
        Difficulty.EZ -> EZ
        Difficulty.HD -> HD
        Difficulty.IN -> IN
        Difficulty.AT -> AT
    }

    fun labelFor(difficulty: Difficulty): String = when (difficulty) {
        Difficulty.EZ -> "EZ"
        Difficulty.HD -> "HD"
        Difficulty.IN -> "IN"
        Difficulty.AT -> "AT"
    }
}
