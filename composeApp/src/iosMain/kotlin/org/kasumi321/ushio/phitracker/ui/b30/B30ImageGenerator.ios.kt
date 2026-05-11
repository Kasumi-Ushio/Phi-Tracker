package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
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
        val titleStyle = textStyle(48f, bold = true)
        val subtitleStyle = textStyle(32f)
        val rksStyle = textStyle(72f, bold = true)
        val centerX = B30ImageSpec.IMAGE_WIDTH / 2f

        val displayName = B30ImageSpec.displayNickname(nickname)
        drawText(canvas, displayName, centerX - measureTextWidth(displayName, titleStyle, titlePaint) / 2, B30ImageSpec.PADDING + 60f, titleStyle, titlePaint)

        val rksText = B30ImageSpec.formatRks(displayRks)
        drawText(canvas, rksText, centerX - measureTextWidth(rksText, rksStyle, rksPaint) / 2, B30ImageSpec.PADDING + 140f, rksStyle, rksPaint)

        val labelText = "Best 30"
        drawText(canvas, labelText, centerX - measureTextWidth(labelText, subtitleStyle, subtitlePaint) / 2, B30ImageSpec.PADDING + 180f, subtitleStyle, subtitlePaint)
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
        drawText(canvas, "#$rank", x + 14f, y + 32f, textStyle(28f, bold = true), rankPaint)

        val namePaint = paint(0xFFFFFFFF.toInt())
        val nameStyle = textStyle(24f, bold = true)
        val songName = truncateText(record.songName, nameStyle, namePaint, (B30ImageSpec.CARD_WIDTH - 30).toFloat())
        drawText(canvas, songName, x + 14f, y + 62f, nameStyle, namePaint)

        val diffPaint = paint(diffColor)
        val diffStyle = textStyle(18f, bold = true)
        val diffLabel = "${B30ImageSpec.difficultyLabel(record.difficulty)} ${B30ImageSpec.formatChartConstant(record.chartConstant)}"
        drawText(canvas, diffLabel, x + 14f, y + 86f, diffStyle, diffPaint)

        if (record.isFullCombo) {
            val fcPaint = paint(0xFF4CAF50.toInt())
            val fcX = x + 14f + measureTextWidth(diffLabel, diffStyle, diffPaint) + 10f
            drawText(canvas, "FC", fcX, y + 86f, textStyle(18f, bold = true), fcPaint)
        }

        val scorePaint = paint(0xFFFFFFFF.toInt())
        drawText(canvas, B30ImageSpec.formatScore(record.score), x + 14f, y + 116f, textStyle(22f, bold = true), scorePaint)

        drawText(canvas, B30ImageSpec.formatAccuracy(record.accuracy), x + 14f, y + 140f, textStyle(18f), paint(0xFFB0BEC5.toInt()))

        val rksPaint = paint(0xFF64B5F6.toInt())
        val rksStyle = textStyle(26f, bold = true)
        val rksText = B30ImageSpec.formatRks(record.rks)
        drawText(
            canvas,
            rksText,
            x + B30ImageSpec.CARD_WIDTH - 14f - measureTextWidth(rksText, rksStyle, rksPaint),
            y + B30ImageSpec.CARD_HEIGHT - 14f,
            rksStyle,
            rksPaint
        )
    }

    private fun drawFooter(canvas: org.jetbrains.skia.Canvas) {
        val footerPaint = paint(0xFF546E7A.toInt())
        val footerStyle = textStyle(22f)
        val text = "Generated by Phigros Score Tracker"
        drawText(
            canvas,
            text,
            B30ImageSpec.IMAGE_WIDTH / 2f - measureTextWidth(text, footerStyle, footerPaint) / 2,
            (B30ImageSpec.IMAGE_HEIGHT - B30ImageSpec.FOOTER_HEIGHT / 2 + 8).toFloat(),
            footerStyle,
            footerPaint
        )
    }

    private fun paint(color: Int): Paint = Paint().apply {
        isAntiAlias = true
        this.color = color
    }

    private data class TextStyleSpec(val size: Float, val bold: Boolean)

    private data class TextRun(val text: String, val typeface: Typeface?)

    private fun textStyle(size: Float, bold: Boolean = false): TextStyleSpec = TextStyleSpec(size, bold)

    private fun font(typeface: Typeface?, style: TextStyleSpec): Font = Font(typeface, style.size).apply {
        isSubpixel = true
    }

    private fun systemTypeface(bold: Boolean): Typeface? {
        val style = if (bold) FontStyle.BOLD else FontStyle.NORMAL
        val families = arrayOf(null, ".SF NS", "SF Pro", "Helvetica Neue", "Helvetica", "PingFang SC", "Hiragino Sans")
        return FontMgr.default.matchFamiliesStyle(families, style)
            ?: FontMgr.default.matchFamilyStyleCharacter(null, style, null, 'A'.code)
    }

    private data class CodePointText(val text: String, val codePoint: Int)

    private val fallbackCache = mutableMapOf<Pair<Boolean, Int>, Typeface?>()

    private fun typefaceFor(codePoint: Int, style: TextStyleSpec): Typeface? {
        val primary = systemTypeface(style.bold)
        if (primary?.getUTF32Glyph(codePoint)?.toInt() != 0) return primary

        val key = style.bold to codePoint
        if (fallbackCache.containsKey(key)) return fallbackCache[key]

        val fontStyle = if (style.bold) FontStyle.BOLD else FontStyle.NORMAL
        val fallback = FontMgr.default.matchFamilyStyleCharacter(
            familyName = null,
            style = fontStyle,
            bcp47 = arrayOf("en", "ja", "zh-Hans"),
            character = codePoint
        )
        val resolved = if (fallback?.getUTF32Glyph(codePoint)?.toInt() != 0) fallback else primary
        fallbackCache[key] = resolved
        return resolved
    }

    private fun codePointTexts(text: String): List<CodePointText> {
        val result = mutableListOf<CodePointText>()
        var index = 0
        while (index < text.length) {
            val high = text[index]
            if (index + 1 < text.length && high.code in 0xD800..0xDBFF) {
                val low = text[index + 1]
                if (low.code in 0xDC00..0xDFFF) {
                    val codePoint = 0x10000 + ((high.code - 0xD800) shl 10) + (low.code - 0xDC00)
                    result += CodePointText("$high$low", codePoint)
                    index += 2
                    continue
                }
            }
            result += CodePointText(high.toString(), high.code)
            index++
        }
        return result
    }

    private fun textRuns(text: String, style: TextStyleSpec): List<TextRun> {
        if (text.isEmpty()) return emptyList()
        val runs = mutableListOf<TextRun>()
        val current = StringBuilder()
        var currentTypeface: Typeface? = null

        for (codePointText in codePointTexts(text)) {
            val typeface = typefaceFor(codePointText.codePoint, style)
            if (currentTypeface != null && typeface != null && currentTypeface.uniqueId != typeface.uniqueId) {
                runs += TextRun(current.toString(), currentTypeface)
                current.clear()
            } else if ((currentTypeface == null) != (typeface == null) && current.isNotEmpty()) {
                runs += TextRun(current.toString(), currentTypeface)
                current.clear()
            }
            currentTypeface = typeface
            current.append(codePointText.text)
        }

        val lastTypeface = currentTypeface
        if (current.isNotEmpty() && lastTypeface != null) {
            runs += TextRun(current.toString(), lastTypeface)
        }
        return runs
    }

    private fun drawText(
        canvas: org.jetbrains.skia.Canvas,
        text: String,
        x: Float,
        y: Float,
        style: TextStyleSpec,
        paint: Paint
    ) {
        var currentX = x
        for (run in textRuns(text, style)) {
            val runFont = font(run.typeface, style)
            canvas.drawString(run.text, currentX, y, runFont, paint)
            currentX += runFont.measureTextWidth(run.text, paint)
        }
    }

    private fun measureTextWidth(text: String, style: TextStyleSpec, paint: Paint): Float {
        var width = 0f
        for (run in textRuns(text, style)) {
            width += font(run.typeface, style).measureTextWidth(run.text, paint)
        }
        return width
    }

    private fun truncateText(text: String, style: TextStyleSpec, paint: Paint, maxWidth: Float): String {
        if (measureTextWidth(text, style, paint) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && measureTextWidth("$truncated…", style, paint) > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }
}
