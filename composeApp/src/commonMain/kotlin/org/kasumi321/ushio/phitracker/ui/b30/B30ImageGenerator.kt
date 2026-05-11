package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.ui.graphics.ImageBitmap
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty

data class B30ImageExport(
    val width: Int,
    val height: Int,
    val pngBytes: ByteArray,
    val preview: ImageBitmap
)

object B30ImageSpec {
    const val CARD_WIDTH = 360
    const val CARD_HEIGHT = 160
    const val COLUMNS = 5
    const val ROWS = 6
    const val PADDING = 16
    const val HEADER_HEIGHT = 200
    const val FOOTER_HEIGHT = 60
    const val GAP = 12

    const val IMAGE_WIDTH = PADDING * 2 + COLUMNS * CARD_WIDTH + (COLUMNS - 1) * GAP
    const val IMAGE_HEIGHT = PADDING * 2 + HEADER_HEIGHT + ROWS * CARD_HEIGHT + (ROWS - 1) * GAP + FOOTER_HEIGHT

    const val DEFAULT_NICKNAME = "Phigros Player"

    const val EZ_COLOR = 0xFF70D866.toInt()
    const val HD_COLOR = 0xFF58B4E3.toInt()
    const val IN_COLOR = 0xFFE34D4D.toInt()
    const val AT_COLOR = 0xFFA855F7.toInt()

    fun displayNickname(nickname: String): String = nickname.ifBlank { DEFAULT_NICKNAME }

    fun formatRks(value: Float): String = formatFixed(value, 4)

    fun formatAccuracy(value: Float): String = "${formatFixed(value, 4)}%"

    fun formatChartConstant(value: Float): String = formatFixed(value, 1)

    fun formatScore(value: Int): String = value.toString().reversed().chunked(3).joinToString(",").reversed()

    fun difficultyLabel(difficulty: Difficulty): String = when (difficulty) {
        Difficulty.EZ -> "EZ"
        Difficulty.HD -> "HD"
        Difficulty.IN -> "IN"
        Difficulty.AT -> "AT"
    }

    fun difficultyColor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EZ -> EZ_COLOR
        Difficulty.HD -> HD_COLOR
        Difficulty.IN -> IN_COLOR
        Difficulty.AT -> AT_COLOR
    }

    private fun formatFixed(value: Float, decimals: Int): String {
        val sign = if (value < 0f) "-" else ""
        val scale = pow10(decimals)
        val rounded = kotlin.math.floor(kotlin.math.abs(value) * scale + 0.5f).toLong()
        val whole = rounded / scale
        val fraction = (rounded % scale).toString().padStart(decimals, '0')
        return "$sign$whole.$fraction"
    }

    private fun pow10(decimals: Int): Int {
        var result = 1
        repeat(decimals) { result *= 10 }
        return result
    }
}

expect object B30ImageGenerator {
    fun generate(
        b30: List<BestRecord>,
        displayRks: Float,
        nickname: String
    ): B30ImageExport
}
