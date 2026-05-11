package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.kasumi321.ushio.phitracker.domain.model.BestRecord

actual object B30ImageGenerator {
    actual fun generate(
        b30: List<BestRecord>,
        displayRks: Float,
        nickname: String
    ): B30ImageExport {
        val surface = Surface.makeRasterN32Premul(B30ImageSpec.IMAGE_WIDTH, B30ImageSpec.IMAGE_HEIGHT)
        val canvas = surface.canvas
        drawBackground(canvas)
        drawHeader(canvas, displayRks, nickname)

        for (i in 0 until minOf(b30.size, 30)) {
            val col = i % B30ImageSpec.COLUMNS
            val row = i / B30ImageSpec.COLUMNS
            val x = B30ImageSpec.PADDING + col * (B30ImageSpec.CARD_WIDTH + B30ImageSpec.GAP)
            val y = B30ImageSpec.PADDING + B30ImageSpec.HEADER_HEIGHT + row * (B30ImageSpec.CARD_HEIGHT + B30ImageSpec.GAP)
            drawCard(canvas, b30[i], i + 1, x.toFloat(), y.toFloat())
        }

        drawFooter(canvas)
        val image = surface.makeImageSnapshot()
        val pngBytes = requireNotNull(image.encodeToData(EncodedImageFormat.PNG, 100)) {
            "Unable to encode B30 image"
        }.bytes

        return B30ImageExport(
            width = B30ImageSpec.IMAGE_WIDTH,
            height = B30ImageSpec.IMAGE_HEIGHT,
            pngBytes = pngBytes,
            preview = image.toComposeImageBitmap()
        )
    }

    private fun drawBackground(canvas: org.jetbrains.skia.Canvas) {
        val paint = Paint().apply {
            shader = Shader.makeLinearGradient(
                0f,
                0f,
                0f,
                B30ImageSpec.IMAGE_HEIGHT.toFloat(),
                intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt(), 0xFF0F3460.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                GradientStyle.DEFAULT
            )
        }
        canvas.drawRect(Rect.makeWH(B30ImageSpec.IMAGE_WIDTH.toFloat(), B30ImageSpec.IMAGE_HEIGHT.toFloat()), paint)
    }

    private fun drawHeader(canvas: org.jetbrains.skia.Canvas, displayRks: Float, nickname: String) {
        val titlePaint = paint(0xFFFFFFFF.toInt())
        val subtitlePaint = paint(0xFFB0BEC5.toInt())
        val rksPaint = paint(0xFF64B5F6.toInt())
        val titleFont = font(48f, bold = true)
        val subtitleFont = font(32f)
        val rksFont = font(72f, bold = true)
        val centerX = B30ImageSpec.IMAGE_WIDTH / 2f

        val displayName = B30ImageSpec.displayNickname(nickname)
        canvas.drawString(displayName, centerX - titleFont.measureTextWidth(displayName, titlePaint) / 2, B30ImageSpec.PADDING + 60f, titleFont, titlePaint)

        val rksText = B30ImageSpec.formatRks(displayRks)
        canvas.drawString(rksText, centerX - rksFont.measureTextWidth(rksText, rksPaint) / 2, B30ImageSpec.PADDING + 140f, rksFont, rksPaint)

        val labelText = "Best 30"
        canvas.drawString(labelText, centerX - subtitleFont.measureTextWidth(labelText, subtitlePaint) / 2, B30ImageSpec.PADDING + 180f, subtitleFont, subtitlePaint)
    }

    private fun drawCard(canvas: org.jetbrains.skia.Canvas, record: BestRecord, rank: Int, x: Float, y: Float) {
        val diffColor = B30ImageSpec.difficultyColor(record.difficulty)
        canvas.drawRRect(
            RRect.makeLTRB(x, y, x + B30ImageSpec.CARD_WIDTH, y + B30ImageSpec.CARD_HEIGHT, 12f),
            paint(0xFF263238.toInt())
        )
        canvas.drawRRect(
            RRect.makeLTRB(x, y, x + 6, y + B30ImageSpec.CARD_HEIGHT, 3f),
            paint(diffColor)
        )

        val rankPaint = paint(
            when (rank) {
                1 -> 0xFFFFD700.toInt()
                2 -> 0xFFC0C0C0.toInt()
                3 -> 0xFFCD7F32.toInt()
                else -> 0xFF78909C.toInt()
            }
        )
        canvas.drawString("#$rank", x + 14f, y + 32f, font(28f, bold = true), rankPaint)

        val namePaint = paint(0xFFFFFFFF.toInt())
        val nameFont = font(24f, bold = true)
        val songName = truncateText(record.songName, nameFont, namePaint, (B30ImageSpec.CARD_WIDTH - 30).toFloat())
        canvas.drawString(songName, x + 14f, y + 62f, nameFont, namePaint)

        val diffPaint = paint(diffColor)
        val diffFont = font(18f, bold = true)
        val diffLabel = "${B30ImageSpec.difficultyLabel(record.difficulty)} ${B30ImageSpec.formatChartConstant(record.chartConstant)}"
        canvas.drawString(diffLabel, x + 14f, y + 86f, diffFont, diffPaint)

        if (record.isFullCombo) {
            val fcPaint = paint(0xFF4CAF50.toInt())
            val fcX = x + 14f + diffFont.measureTextWidth(diffLabel, diffPaint) + 10f
            canvas.drawString("FC", fcX, y + 86f, font(18f, bold = true), fcPaint)
        }

        val scorePaint = paint(0xFFFFFFFF.toInt())
        canvas.drawString(B30ImageSpec.formatScore(record.score), x + 14f, y + 116f, font(22f, bold = true), scorePaint)

        canvas.drawString(B30ImageSpec.formatAccuracy(record.accuracy), x + 14f, y + 140f, font(18f), paint(0xFFB0BEC5.toInt()))

        val rksPaint = paint(0xFF64B5F6.toInt())
        val rksFont = font(26f, bold = true)
        val rksText = B30ImageSpec.formatRks(record.rks)
        canvas.drawString(
            rksText,
            x + B30ImageSpec.CARD_WIDTH - 14f - rksFont.measureTextWidth(rksText, rksPaint),
            y + B30ImageSpec.CARD_HEIGHT - 14f,
            rksFont,
            rksPaint
        )
    }

    private fun drawFooter(canvas: org.jetbrains.skia.Canvas) {
        val footerPaint = paint(0xFF546E7A.toInt())
        val footerFont = font(22f)
        val text = "Generated by Phigros Score Tracker"
        canvas.drawString(
            text,
            B30ImageSpec.IMAGE_WIDTH / 2f - footerFont.measureTextWidth(text, footerPaint) / 2,
            (B30ImageSpec.IMAGE_HEIGHT - B30ImageSpec.FOOTER_HEIGHT / 2 + 8).toFloat(),
            footerFont,
            footerPaint
        )
    }

    private fun paint(color: Int): Paint = Paint().apply {
        isAntiAlias = true
        this.color = color
    }

    private fun font(size: Float, bold: Boolean = false): Font = Font().apply {
        this.size = size
        isEmboldened = bold
        isSubpixel = true
    }

    private fun truncateText(text: String, font: Font, paint: Paint, maxWidth: Float): String {
        if (font.measureTextWidth(text, paint) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && font.measureTextWidth("$truncated…", paint) > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }
}
