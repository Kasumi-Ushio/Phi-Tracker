package org.kasumi321.ushio.phitracker.ui.b30

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.asImageBitmap
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import java.io.ByteArrayOutputStream

actual object B30ImageGenerator {
    actual fun generate(
        b30: List<BestRecord>,
        displayRks: Float,
        nickname: String
    ): B30ImageExport {
        val bitmap = Bitmap.createBitmap(B30ImageSpec.IMAGE_WIDTH, B30ImageSpec.IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
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

        val pngBytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode B30 image" }
            output.toByteArray()
        }

        return B30ImageExport(
            width = B30ImageSpec.IMAGE_WIDTH,
            height = B30ImageSpec.IMAGE_HEIGHT,
            pngBytes = pngBytes,
            preview = bitmap.asImageBitmap()
        )
    }

    private fun drawBackground(canvas: Canvas) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                B30ImageSpec.IMAGE_HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), Color.parseColor("#0f3460")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, B30ImageSpec.IMAGE_WIDTH.toFloat(), B30ImageSpec.IMAGE_HEIGHT.toFloat(), bgPaint)
    }

    private fun drawHeader(canvas: Canvas, displayRks: Float, nickname: String) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0BEC5")
            textSize = 32f
        }
        val rksPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64B5F6")
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val centerX = B30ImageSpec.IMAGE_WIDTH / 2f
        val displayName = B30ImageSpec.displayNickname(nickname)
        canvas.drawText(displayName, centerX - titlePaint.measureText(displayName) / 2, B30ImageSpec.PADDING + 60f, titlePaint)

        val rksText = B30ImageSpec.formatRks(displayRks)
        canvas.drawText(rksText, centerX - rksPaint.measureText(rksText) / 2, B30ImageSpec.PADDING + 140f, rksPaint)

        val labelText = "Best 30"
        canvas.drawText(labelText, centerX - subtitlePaint.measureText(labelText) / 2, B30ImageSpec.PADDING + 180f, subtitlePaint)
    }

    private fun drawCard(canvas: Canvas, record: BestRecord, rank: Int, x: Float, y: Float) {
        val diffColor = B30ImageSpec.difficultyColor(record.difficulty)

        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#263238") }
        val rect = RectF(x, y, x + B30ImageSpec.CARD_WIDTH, y + B30ImageSpec.CARD_HEIGHT)
        canvas.drawRoundRect(rect, 12f, 12f, cardBg)

        val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = diffColor }
        canvas.drawRoundRect(RectF(x, y, x + 6, y + B30ImageSpec.CARD_HEIGHT), 3f, 3f, stripPaint)

        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (rank) {
                1 -> Color.parseColor("#FFD700")
                2 -> Color.parseColor("#C0C0C0")
                3 -> Color.parseColor("#CD7F32")
                else -> Color.parseColor("#78909C")
            }
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("#$rank", x + 14f, y + 32f, rankPaint)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val songName = truncateText(record.songName, namePaint, (B30ImageSpec.CARD_WIDTH - 30).toFloat())
        canvas.drawText(songName, x + 14f, y + 62f, namePaint)

        val diffLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = diffColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val diffLabel = "${B30ImageSpec.difficultyLabel(record.difficulty)} ${B30ImageSpec.formatChartConstant(record.chartConstant)}"
        canvas.drawText(diffLabel, x + 14f, y + 86f, diffLabelPaint)

        if (record.isFullCombo) {
            val fcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4CAF50")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fcX = x + 14f + diffLabelPaint.measureText(diffLabel) + 10f
            canvas.drawText("FC", fcX, y + 86f, fcPaint)
        }

        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(B30ImageSpec.formatScore(record.score), x + 14f, y + 116f, scorePaint)

        val accPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0BEC5")
            textSize = 18f
        }
        canvas.drawText(B30ImageSpec.formatAccuracy(record.accuracy), x + 14f, y + 140f, accPaint)

        val rksPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64B5F6")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rksText = B30ImageSpec.formatRks(record.rks)
        canvas.drawText(
            rksText,
            x + B30ImageSpec.CARD_WIDTH - 14f - rksPaint.measureText(rksText),
            y + B30ImageSpec.CARD_HEIGHT - 14f,
            rksPaint
        )
    }

    private fun drawFooter(canvas: Canvas) {
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#546E7A")
            textSize = 22f
        }
        val text = "Generated by Phigros Score Tracker"
        canvas.drawText(
            text,
            B30ImageSpec.IMAGE_WIDTH / 2f - footerPaint.measureText(text) / 2,
            (B30ImageSpec.IMAGE_HEIGHT - B30ImageSpec.FOOTER_HEIGHT / 2 + 8).toFloat(),
            footerPaint
        )
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }
}
