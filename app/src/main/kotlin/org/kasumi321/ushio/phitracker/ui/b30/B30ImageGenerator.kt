package org.kasumi321.ushio.phitracker.ui.b30

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import java.io.File
import java.io.FileOutputStream

/**
 * B30 图片生成器
 *
 * 使用 Android Canvas 绘制 B30 成绩图
 * 布局: 5 列 x 6 行 = 30 格
 */
object B30ImageGenerator {

    private const val CARD_WIDTH = 360
    private const val CARD_HEIGHT = 160
    private const val COLUMNS = 5
    private const val ROWS = 6
    private const val PADDING = 16
    private const val HEADER_HEIGHT = 200
    private const val FOOTER_HEIGHT = 60
    private const val GAP = 12

    private val IMAGE_WIDTH = PADDING * 2 + COLUMNS * CARD_WIDTH + (COLUMNS - 1) * GAP
    private val IMAGE_HEIGHT = PADDING * 2 + HEADER_HEIGHT + ROWS * CARD_HEIGHT + (ROWS - 1) * GAP + FOOTER_HEIGHT

    fun generate(
        b30: List<BestRecord>,
        displayRks: Float,
        nickname: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 背景渐变
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
                intArrayOf(Color.parseColor("#1a1a2e"), Color.parseColor("#16213e"), Color.parseColor("#0f3460")),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), bgPaint)

        // 绘制头部
        drawHeader(canvas, displayRks, nickname)

        // 绘制 30 张卡片
        for (i in 0 until minOf(b30.size, 30)) {
            val col = i % COLUMNS
            val row = i / COLUMNS
            val x = PADDING + col * (CARD_WIDTH + GAP)
            val y = PADDING + HEADER_HEIGHT + row * (CARD_HEIGHT + GAP)
            drawCard(canvas, b30[i], i + 1, x.toFloat(), y.toFloat())
        }

        // 绘制底部水印
        drawFooter(canvas)

        return bitmap
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

        val centerX = IMAGE_WIDTH / 2f

        // 标题
        canvas.drawText(
            nickname.ifBlank { "Phigros Player" },
            centerX - titlePaint.measureText(nickname.ifBlank { "Phigros Player" }) / 2,
            PADDING + 60f,
            titlePaint
        )

        // RKS
        val rksText = String.format("%.4f", displayRks)
        canvas.drawText(
            rksText,
            centerX - rksPaint.measureText(rksText) / 2,
            PADDING + 140f,
            rksPaint
        )

        // Best 30 标签
        val labelText = "Best 30"
        canvas.drawText(
            labelText,
            centerX - subtitlePaint.measureText(labelText) / 2,
            PADDING + 180f,
            subtitlePaint
        )
    }

    private fun drawCard(canvas: Canvas, record: BestRecord, rank: Int, x: Float, y: Float) {
        val diffColor = getDiffColor(record.difficulty)

        // 卡片背景
        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#263238")
        }
        val rect = RectF(x, y, x + CARD_WIDTH, y + CARD_HEIGHT)
        canvas.drawRoundRect(rect, 12f, 12f, cardBg)

        // 难度色条 (左边)
        val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = diffColor }
        canvas.drawRoundRect(RectF(x, y, x + 6, y + CARD_HEIGHT), 3f, 3f, stripPaint)

        // 排名
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

        // 曲名
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val songName = truncateText(record.songName, namePaint, (CARD_WIDTH - 30).toFloat())
        canvas.drawText(songName, x + 14f, y + 62f, namePaint)

        // 难度标签
        val diffLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = diffColor
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val diffLabel = "${getDiffLabel(record.difficulty)} ${String.format("%.1f", record.chartConstant)}"
        canvas.drawText(diffLabel, x + 14f, y + 86f, diffLabelPaint)

        // FC 标签
        if (record.isFullCombo) {
            val fcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4CAF50")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fcX = x + 14f + diffLabelPaint.measureText(diffLabel) + 10f
            canvas.drawText("FC", fcX, y + 86f, fcPaint)
        }

        // Score
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val scoreText = String.format("%,d", record.score)
        canvas.drawText(scoreText, x + 14f, y + 116f, scorePaint)

        // ACC
        val accPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B0BEC5")
            textSize = 18f
        }
        canvas.drawText(
            String.format("%.4f%%", record.accuracy),
            x + 14f,
            y + 140f,
            accPaint
        )

        // RKS (右下角)
        val rksPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64B5F6")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rksText = String.format("%.4f", record.rks)
        canvas.drawText(
            rksText,
            x + CARD_WIDTH - 14f - rksPaint.measureText(rksText),
            y + CARD_HEIGHT - 14f,
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
            IMAGE_WIDTH / 2f - footerPaint.measureText(text) / 2,
            (IMAGE_HEIGHT - FOOTER_HEIGHT / 2 + 8).toFloat(),
            footerPaint
        )
    }

    private fun getDiffColor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EZ -> Color.parseColor("#70D866")
        Difficulty.HD -> Color.parseColor("#58B4E3")
        Difficulty.IN -> Color.parseColor("#E34D4D")
        Difficulty.AT -> Color.parseColor("#A855F7")
    }

    private fun getDiffLabel(difficulty: Difficulty): String = when (difficulty) {
        Difficulty.EZ -> "EZ"
        Difficulty.HD -> "HD"
        Difficulty.IN -> "IN"
        Difficulty.AT -> "AT"
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }

    /**
     * 保存 Bitmap 到 Pictures 目录
     * @return 保存的文件路径, 或 null 如果保存失败
     */
    fun saveToPictures(context: Context, bitmap: Bitmap, filename: String = "B30_${System.currentTimeMillis()}.png"): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhiTracker")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                it.toString()
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PhiTracker")
            dir.mkdirs()
            val file = File(dir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        }
    }
}
