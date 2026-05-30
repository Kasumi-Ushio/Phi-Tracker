package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.GradientStyle
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.Image as SkiaImage
import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import kotlin.math.ceil

actual object B30ImageGenerator {

    /** Reusable Ktor client for downloading network images on iOS. */
    private val httpClient by lazy {
        HttpClient(Darwin) {
            engine {
                configureRequest {
                    setAllowsCellularAccess(true)
                }
            }
        }
    }

    // ================================================================
    // Layout dimensions derived from B30ExportSpec (commonMain).
    // Pixel values = spec dp * density.
    // ================================================================
    private val density = B30ExportSpec.DENSITY
    private fun dpToPx(dp: Float): Float = dp * density

    // Card grid dimensions from B30ExportSpec
    private val pagePadding = dpToPx(B30ExportSpec.PAGE_PADDING_DP)
    private val cardWidth = dpToPx(B30ExportSpec.cardWidthDp)
    private val cardHeight = dpToPx(B30ExportSpec.cardHeightDp)
    private val cardGapH = dpToPx(B30ExportSpec.cardHorizontalGapDp)
    private val cardGapV = dpToPx(B30ExportSpec.cardVerticalGapDp)

    // Header row dimensions from B30ExportSpec
    private val headerCardHeight = dpToPx(B30ExportSpec.profileCardHeightDp)
    private val profileCardWidth = dpToPx(B30ExportSpec.profileCardWidthDp)
    private val statsCardWidth = dpToPx(B30ExportSpec.statsCardWidthDp)

    // Header row gap: SpaceBetween fills remaining content width
    private val headerCardGap = dpToPx(
        B30ExportSpec.contentWidthDp - B30ExportSpec.profileCardWidthDp - B30ExportSpec.statsCardWidthDp
    )

    // Output width from B30ExportSpec
    private val contentWidth = B30ExportSpec.WIDTH_PX.toFloat()

    // Section spacings derived from B30ExportLayout dp spacers
    private val headerBottomSpacing = dpToPx(12f)   // Spacer(12.dp) after header
    private val sectionSpacing = dpToPx(8f)          // Spacer(8.dp) between sections
    private val footerSpacingBefore = dpToPx(8f)     // Spacer(8.dp) before footer

    // Section/footer layout heights derived from B30ExportSpec (commonMain).
    // These approximate the auto-layout behaviour of B30ExportLayout's Compose
    // rendering; Skia's imperative canvas cannot compute them automatically.
    private val sectionTitleHeight = dpToPx(B30ExportSpec.sectionTitleHeightDp)
    private val footerHeight = dpToPx(B30ExportSpec.footerHeightDp)
    private val footerTextOffset = dpToPx(B30ExportSpec.footerTextOffsetDp)

    // Profile card internal layout derived from B30ExportLayout call sites.
    private val profileContentHPadding = dpToPx(9f)
    private val profileContentVPadding = dpToPx(5f)
    private val profileAvatarSize = dpToPx(61.2f)
    private val profileAvatarTextSpacing = dpToPx(18f)
    private val profileTextVerticalSpacing = dpToPx(2f)

    // Stats card internal layout derived from B30ExportLayout call sites.
    private val statsContentHPadding = dpToPx(9f)
    private val statsContentVPadding = dpToPx(5f)
    private val statsRowSpacing = dpToPx(7f)

    // Score card compact layout derived from B30ExportLayout + beta5 baseline.
    private val scoreContentHPadding = dpToPx(9f)
    private val scoreContentVPadding = dpToPx(5f)
    private val scoreRankBoxSize = dpToPx(36f)
    private val scoreRankAfterSpacing = dpToPx(8f)
    private val scoreThumbnailAfterSpacing = dpToPx(10f)

    /** Max dimension (px) for the bitmap passed to the Skia blur to keep memory bounded. */
    private const val MAX_BLUR_BITMAP_DIM = 800

    actual suspend fun generate(exportData: B30ExportData): B30ImageExport {
        val totalHeightPx = computeTotalHeight(exportData)
        val surface = Surface.makeRasterN32Premul(B30ExportSpec.WIDTH_PX, totalHeightPx)
        val canvas = surface.canvas

        drawBackground(canvas, totalHeightPx, exportData)
        var y = drawHeaderSection(canvas, pagePadding, exportData)
        y += headerBottomSpacing
        y = drawSection(canvas, y, "Phi", exportData.phiRecords) { "P${it + 1}" }
        y += sectionSpacing
        y = drawSection(canvas, y, "Best 27", exportData.bestRecords) { "#${it + 1}" }
        if (exportData.overflowRecords.isNotEmpty()) {
            y += sectionSpacing
            y = drawSection(canvas, y, "OVERFLOW", exportData.overflowRecords) { "#${it + 1}" }
        }
        drawFooter(canvas, totalHeightPx, exportData.dateText)

        val image = surface.makeImageSnapshot()
        val pngBytes = requireNotNull(image.encodeToData(EncodedImageFormat.PNG, 100)) {
            "Unable to encode B30 image"
        }.bytes

        return B30ImageExport(
            width = B30ExportSpec.WIDTH_PX,
            height = totalHeightPx,
            pngBytes = pngBytes,
            preview = image.toComposeImageBitmap()
        )
    }

    private fun computeTotalHeight(data: B30ExportData): Int {
        var h = pagePadding
        h += headerCardHeight + headerBottomSpacing
        h += sectionTitleHeight + gridHeight(data.phiRecords.size)
        h += sectionSpacing + sectionTitleHeight + gridHeight(data.bestRecords.size)
        if (data.overflowRecords.isNotEmpty()) {
            h += sectionSpacing + sectionTitleHeight + gridHeight(data.overflowRecords.size)
        }
        h += footerSpacingBefore + footerHeight + pagePadding
        return ceil(h).toInt()
    }

    private fun gridHeight(cardCount: Int): Float {
        if (cardCount == 0) return 0f
        val rows = ceil(cardCount / 3.0).toInt()
        return rows * cardHeight + (rows - 1) * cardGapV
    }

    // ---- background / image loading ----

    /**
     * Draws the export background. When [exportData.backgroundUri] is non-null we
     * attempt to load and blur the decoded Skia image from either a local file or
     * a network URI. If loading succeeds the background is downscaled to at most
     * [MAX_BLUR_BITMAP_DIM] px on the longest side, then softened with a Skia
     * [ImageFilter.makeBlur] before being scaled back up to fill the canvas.
     * When loading fails (or the URI cannot be decoded) a deterministic soft
     * gradient keyed on the URI string provides a fallback. A white overlay
     * (65 % opacity) is always applied on top.
     */
    private suspend fun drawBackground(canvas: org.jetbrains.skia.Canvas, height: Int, exportData: B30ExportData) {
        val bgUri = exportData.backgroundUri
        val bgImage = if (bgUri != null) loadAndBlurBackground(bgUri) else null

        if (bgImage != null) {
            val imgW = bgImage.width.toFloat()
            val imgH = bgImage.height.toFloat()
            val canvasW = contentWidth
            val canvasH = height.toFloat()
            val scale = maxOf(canvasW / imgW, canvasH / imgH)
            val drawW = imgW * scale
            val drawH = imgH * scale
            val offsetX = (canvasW - drawW) / 2f
            val offsetY = (canvasH - drawH) / 2f
            canvas.drawImageRect(
                bgImage,
                Rect.makeWH(imgW, imgH),
                Rect.makeXYWH(offsetX, offsetY, drawW, drawH)
            )
        } else if (bgUri != null) {
            // Fallback: deterministic soft gradient from URI string
            val gradient = uriGradient(bgUri, height)
            canvas.drawRect(Rect.makeWH(contentWidth, height.toFloat()), gradient)
        } else {
            // Default background
            val basePaint = Paint().apply {
                shader = Shader.makeLinearGradient(
                    0f, 0f,
                    0f, height.toFloat(),
                    intArrayOf(0xFFF0F0F5.toInt(), 0xFFE8E8EE.toInt(), 0xFFEDEDF2.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    GradientStyle.DEFAULT
                )
            }
            canvas.drawRect(Rect.makeWH(contentWidth, height.toFloat()), basePaint)
        }

        // White mask overlay (blur-equivalent)
        val maskPaint = Paint().apply {
            color = 0xA6FFFFFF.toInt()
            isAntiAlias = true
        }
        canvas.drawRect(Rect.makeWH(contentWidth, height.toFloat()), maskPaint)
    }

    /**
     * Loads the background image from [uri] and applies a Skia blur.
     *
     * The image is first downscaled to at most [MAX_BLUR_BITMAP_DIM] px on the
     * longest side (memory bounding), then softened with a Gaussian-style
     * [ImageFilter.makeBlur]. The resulting blurred image is scaled back to fill
     * the export canvas in [drawBackground].
     *
     * Returns null if the file cannot be loaded or decoded.
     */
    private suspend fun loadAndBlurBackground(uri: String): SkiaImage? {
        val original = loadSkiaImage(uri) ?: return null
        val maxDim = MAX_BLUR_BITMAP_DIM
        val scale = if (maxOf(original.width, original.height) <= maxDim) {
            1f
        } else {
            maxDim.toFloat() / maxOf(original.width, original.height)
        }
        val w = (original.width * scale).toInt().coerceAtLeast(1)
        val h = (original.height * scale).toInt().coerceAtLeast(1)

        // Downscale by rendering to a smaller Surface
        val downSurface = Surface.makeRasterN32Premul(w, h)
        if (scale < 1f) {
            downSurface.canvas.scale(scale, scale)
        }
        downSurface.canvas.drawImage(original, 0f, 0f)
        val downscaled = downSurface.makeImageSnapshot()

        // Apply Skia blur (sigma ~15 on downscaled ≈ StackBlur radius 50 on full-size)
        val blurSigmaX = 15f
        val blurSigmaY = 15f
        val blurFilter = ImageFilter.makeBlur(blurSigmaX, blurSigmaY, FilterTileMode.CLAMP, null)
        val blurPaint = Paint().apply {
            imageFilter = blurFilter
            isAntiAlias = true
        }
        val blurSurface = Surface.makeRasterN32Premul(w, h)
        blurSurface.canvas.drawImage(downscaled, 0f, 0f, blurPaint)
        return blurSurface.makeImageSnapshot()
    }

    /**
     * Attempts to decode an image from a file path or network URI using
     * okio/Skia for local files and Ktor for http(s) URIs.
     * Returns null if the data cannot be decoded into a valid Skia image.
     */
    private suspend fun loadSkiaImage(uri: String): SkiaImage? {
        val bytes = try {
            when {
                uri.startsWith("http://") || uri.startsWith("https://") -> {
                    httpClient.get(uri).bodyAsBytes()
                }
                else -> {
                    val path = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
                    FileSystem.SYSTEM.read(path.toPath()) { readByteArray() }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return try {
            SkiaImage.makeFromEncoded(bytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Produces a deterministic soft gradient from the given string by hashing its
     * characters. This ensures that different background selections produce visibly
     * different outputs even when the image file cannot be decoded.
     */
    private fun uriGradient(uri: String, height: Int): Paint {
        var hash = 0L
        for (ch in uri) {
            hash = hash * 31 + ch.code.toLong()
        }
        val r = ((hash ushr 16) and 0xFF).toInt()
        val g = ((hash ushr 8) and 0xFF).toInt()
        val b = (hash and 0xFF).toInt()
        val base = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        val soft = blendColor(base, 0xFFF0F0F5.toInt(), 0.7f)
        val soft2 = blendColor(base, 0xFFE8E8EE.toInt(), 0.5f)
        return Paint().apply {
            shader = Shader.makeLinearGradient(
                0f, 0f,
                0f, height.toFloat(),
                intArrayOf(soft, soft2, soft),
                floatArrayOf(0f, 0.5f, 1f),
                GradientStyle.DEFAULT
            )
        }
    }

    /** Linear interpolation between two ARGB colours by [t] (0->1). */
    private fun blendColor(c1: Int, c2: Int, t: Float): Int {
        val a = ((c1 ushr 24) and 0xFF) + ((((c2 ushr 24) and 0xFF) - ((c1 ushr 24) and 0xFF)) * t).toInt()
        val r = ((c1 ushr 16) and 0xFF) + ((((c2 ushr 16) and 0xFF) - ((c1 ushr 16) and 0xFF)) * t).toInt()
        val g = ((c1 ushr 8) and 0xFF) + ((((c2 ushr 8) and 0xFF) - ((c1 ushr 8) and 0xFF)) * t).toInt()
        val b = (c1 and 0xFF) + (((c2 and 0xFF) - (c1 and 0xFF)) * t).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private suspend fun drawHeaderSection(
        canvas: org.jetbrains.skia.Canvas,
        topY: Float,
        data: B30ExportData
    ): Float {
        val headerY = topY
        drawProfileCard(canvas, pagePadding, headerY, data)
        drawStatsCard(canvas, pagePadding + profileCardWidth + headerCardGap, headerY, data)
        return headerY + headerCardHeight
    }

    private suspend fun drawProfileCard(
        canvas: org.jetbrains.skia.Canvas,
        x: Float,
        y: Float,
        data: B30ExportData
    ) {
        canvas.drawRRect(
            RRect.makeLTRB(x, y, x + profileCardWidth, y + headerCardHeight, 16f),
            paint(0xFFFFFFFF.toInt())
        )

        val avatarDiameter = profileAvatarSize
        val avatarRadius = avatarDiameter / 2f
        val avatarLeft = x + profileContentHPadding
        val avatarTop = y + profileContentVPadding + (headerCardHeight - profileContentVPadding * 2 - avatarDiameter) / 2f

        val avatarImage = data.avatarUri?.let { loadSkiaImage(it) }
        if (avatarImage != null) {
            canvas.save()
            val circle = RRect.makeLTRB(
                avatarLeft, avatarTop,
                avatarLeft + avatarDiameter, avatarTop + avatarDiameter,
                avatarRadius
            )
            canvas.clipRRect(circle, true)
            val imgW = avatarImage.width.toFloat()
            val imgH = avatarImage.height.toFloat()
            val scale = maxOf(avatarDiameter / imgW, avatarDiameter / imgH)
            val drawW = imgW * scale
            val drawH = imgH * scale
            val offsetX = avatarLeft + (avatarDiameter - drawW) / 2f
            val offsetY = avatarTop + (avatarDiameter - drawH) / 2f
            canvas.drawImageRect(
                avatarImage,
                Rect.makeWH(imgW, imgH),
                Rect.makeXYWH(offsetX, offsetY, drawW, drawH)
            )
            canvas.restore()
        }

        val innerX = x + if (avatarImage != null) {
            profileContentHPadding + profileAvatarSize + profileAvatarTextSpacing
        } else {
            profileContentHPadding
        }
        val displayName = data.nickname.ifBlank { "Phigros Player" }

        val namePaint = paint(0xFF1A1A2E.toInt())
        val nameStyle = textStyle(48f, bold = true)
        drawText(canvas, displayName, innerX, y + 58f, nameStyle, namePaint)

        val moneyStr = data.moneyString
        if (moneyStr.isNotBlank()) {
            val dataLabelPaint = paint(0xFF6B7280.toInt())
            val dataLabelStyle = textStyle(28f)
            drawText(canvas, "Data: $moneyStr", innerX, y + 98f, dataLabelStyle, dataLabelPaint)
        }

        val rksPaint = paint(0xFF2563EB.toInt())
        val rksStyle = textStyle(60f, bold = true)
        val rksText = B30ImageSpec.formatRks(data.rks)
        var rksX = innerX
        drawText(canvas, rksText, rksX, y + 160f, rksStyle, rksPaint)
        rksX += measureTextWidth(rksText, rksStyle, rksPaint) + 12f

        if (data.challengeLevel > 0) {
            val tier = data.challengeLevel / 100
            val level = data.challengeLevel % 100
            val rankText = "$level"
            val rankStyle = textStyle(28f, bold = true)

            val badgeW = measureTextWidth(rankText, rankStyle, paint(0xFFFFFFFF.toInt())) + 20f
            val badgeH = 40f
            val badgeY = y + 160f - badgeH + 8f
            val badgeColor = challengeBadgeColor(tier)
            val badgeTextColor = challengeBadgeTextColor(tier)

            canvas.drawRRect(
                RRect.makeLTRB(rksX, badgeY, rksX + badgeW, badgeY + badgeH, 6f),
                paint(badgeColor)
            )
            val centerOffsetX = (badgeW - measureTextWidth(rankText, rankStyle, paint(badgeTextColor))) / 2f
            drawText(canvas, rankText, rksX + centerOffsetX, badgeY + 30f, rankStyle, paint(badgeTextColor))
        }
    }

    private fun challengeBadgeColor(tier: Int): Int = when (tier) {
        0 -> 0xFFCCCCCC.toInt()
        1 -> 0xFF4CAF50.toInt()
        2 -> 0xFF2196F3.toInt()
        3 -> 0xFFF44336.toInt()
        4 -> 0xFFFFD700.toInt()
        5 -> 0xFF8B5CF6.toInt()
        else -> 0xFFCCCCCC.toInt()
    }

    private fun challengeBadgeTextColor(tier: Int): Int = when (tier) {
        0 -> 0xFF333333.toInt()
        4 -> 0xFF5D4037.toInt()
        5 -> 0xFFFFFFFF.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    private fun drawStatsCard(
        canvas: org.jetbrains.skia.Canvas,
        x: Float,
        y: Float,
        data: B30ExportData
    ) {
        canvas.drawRRect(
            RRect.makeLTRB(x, y, x + statsCardWidth, y + headerCardHeight, 16f),
            paint(0xFFFFFFFF.toInt())
        )

        val stats = data.statsTable
        val difficulties = listOf("EZ", "HD", "IN", "AT")
        val diffColors = mapOf(
            "EZ" to 0xFF70D866.toInt(),
            "HD" to 0xFF58B4E3.toInt(),
            "IN" to 0xFFE34D4D.toInt(),
            "AT" to 0xFFA855F7.toInt()
        )

        val contentCenterY = y + headerCardHeight / 2f
        val diffRowY = contentCenterY - statsRowSpacing / 2f - 24f
        val diffItemWidth = statsCardWidth / 4f
        for ((idx, label) in difficulties.withIndex()) {
            val cnt = stats.clearCounts[label] ?: 0
            val cx = x + diffItemWidth * idx + diffItemWidth / 2f

            val labelStyle = textStyle(28f, bold = true)
            val labelColor = diffColors[label] ?: 0xFF1A1A2E.toInt()
            val labelW = measureTextWidth(label, labelStyle, paint(labelColor))
            drawText(canvas, label, cx - labelW / 2f, diffRowY, labelStyle, paint(labelColor))

            val cntStyle = textStyle(36f, bold = true)
            val cntText = "$cnt"
            val cntColor = 0xFF1A1A2E.toInt()
            val cntW = measureTextWidth(cntText, cntStyle, paint(cntColor))
            drawText(canvas, cntText, cx - cntW / 2f, diffRowY + 48f, cntStyle, paint(cntColor))
        }

        val badgeRowY = contentCenterY + statsRowSpacing / 2f + 12f
        drawBadgeWithCount(canvas, x + 36f, badgeRowY, "FC", stats.fcCount, 0xFF4FC3F7.toInt(), 0xFFFFFFFF.toInt(), 0xFF1A1A2E.toInt())
        drawPhiBadgeWithCount(canvas, x + 36f, badgeRowY, stats)
    }

    private fun drawPhiBadgeWithCount(
        canvas: org.jetbrains.skia.Canvas,
        startX: Float,
        rowY: Float,
        stats: B30StatsTable
    ) {
        val fcLabelStyle = textStyle(24f, bold = true)
        val fcCntStyle = textStyle(36f, bold = true)
        val fcBadgeW = measureTextWidth("FC", fcLabelStyle, paint(0xFFFFFFFF.toInt())) + 18f
        val fcCntW = measureTextWidth("${stats.fcCount}", fcCntStyle, paint(0xFF1A1A2E.toInt()))
        val phiBadgeX = startX + fcBadgeW + 16f + fcCntW + 32f

        drawBadgeWithCount(canvas, phiBadgeX, rowY, "\u03C6", stats.phiCount, 0xFFFFD54F.toInt(), 0xFF5D4037.toInt(), 0xFF1A1A2E.toInt())
    }

    private fun drawBadgeWithCount(
        canvas: org.jetbrains.skia.Canvas,
        badgeX: Float,
        rowY: Float,
        label: String,
        count: Int,
        badgeBg: Int,
        badgeTextColor: Int,
        countColor: Int
    ) {
        val labelStyle = textStyle(24f, bold = true)
        val labelW = measureTextWidth(label, labelStyle, paint(badgeTextColor))
        val badgeW = labelW + 18f
        val badgeH = 36f
        val badgeY = rowY - badgeH + 6f

        canvas.drawRRect(
            RRect.makeLTRB(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 5f),
            paint(badgeBg)
        )
        drawText(canvas, label, badgeX + (badgeW - labelW) / 2f, badgeY + 27f, labelStyle, paint(badgeTextColor))

        val cntStyle = textStyle(36f, bold = true)
        drawText(canvas, "$count", badgeX + badgeW + 16f, rowY, cntStyle, paint(countColor))
    }

    private suspend fun drawSection(
        canvas: org.jetbrains.skia.Canvas,
        startY: Float,
        title: String,
        cards: List<ExportCardData>,
        rankLabelProvider: (Int) -> String
    ): Float {
        if (cards.isEmpty()) return startY

        var y = startY

        val titleStyle = textStyle(48f, bold = true)
        val titlePaint = paint(0xFF2563EB.toInt())
        val titleW = measureTextWidth(title, titleStyle, titlePaint)
        drawText(canvas, title, (contentWidth - titleW) / 2f, y + 54f, titleStyle, titlePaint)

        y += sectionTitleHeight

        val rows = ceil(cards.size / 3.0).toInt()
        for (row in 0 until rows) {
            val rowY = y + row * (cardHeight + cardGapV)
            for (col in 0 until 3) {
                val idx = row * 3 + col
                if (idx >= cards.size) break
                val cardX = pagePadding + col * (cardWidth + cardGapH)
                drawCard(canvas, cardX, rowY, cards[idx], rankLabelProvider(idx))
            }
        }

        return y + rows * cardHeight + (rows - 1) * cardGapV
    }

    private suspend fun drawCard(
        canvas: org.jetbrains.skia.Canvas,
        x: Float,
        y: Float,
        card: ExportCardData,
        rankLabel: String
    ) {
        val record = card.record
        val diffColor = B30ImageSpec.difficultyColor(record.difficulty)

        canvas.drawRRect(
            RRect.makeLTRB(x, y, x + cardWidth, y + cardHeight, 12f),
            paint(0xFFFFFFFF.toInt())
        )
        canvas.drawRRect(
            RRect.makeLTRB(x, y, x + 6f, y + cardHeight, 3f),
            paint(diffColor)
        )

        val pad = scoreContentHPadding
        val thumbSize = dpToPx(56f * 0.9f)
        val thumbRadius = dpToPx(8f)

        // ---- Rank label (left column) ----
        val rankStyle = textStyle(32f, bold = true)
        val rankColor = when {
            rankLabel.startsWith("P1") || rankLabel == "#1" -> 0xFFFFD700.toInt()
            rankLabel.startsWith("P2") || rankLabel == "#2" -> 0xFFC0C0C0.toInt()
            rankLabel.startsWith("P3") || rankLabel == "#3" -> 0xFFCD7F32.toInt()
            else -> 0xFF6B7280.toInt()
        }
        val rankPaint = paint(rankColor)
        val rankLabelWidth = measureTextWidth(rankLabel, rankStyle, rankPaint)
        val rankLeft = x + pad
        val rankBoxWidth = scoreRankBoxSize
        val rankCenterX = rankLeft + (rankBoxWidth - rankLabelWidth) / 2f
        drawText(canvas, rankLabel, rankCenterX, y + 44f, rankStyle, rankPaint)

        // ---- Thumbnail (after fixed rank column + spacer) ----
        val rankSpacer = scoreRankAfterSpacing
        val thumbLeft = rankLeft + scoreRankBoxSize + rankSpacer
        val thumbTop = y + (cardHeight - thumbSize) / 2f
        val hasIllustration = card.illustrationUri != null

        if (hasIllustration) {
            val thumbImage = loadSkiaImage(card.illustrationUri)
            if (thumbImage != null) {
                canvas.save()
                canvas.clipRRect(
                    RRect.makeLTRB(
                        thumbLeft, thumbTop,
                        thumbLeft + thumbSize, thumbTop + thumbSize,
                        thumbRadius
                    ),
                    true
                )
                val imgW = thumbImage.width.toFloat()
                val imgH = thumbImage.height.toFloat()
                val scale = maxOf(thumbSize / imgW, thumbSize / imgH)
                val drawW = imgW * scale
                val drawH = imgH * scale
                val offsetX = thumbLeft + (thumbSize - drawW) / 2f
                val offsetY = thumbTop + (thumbSize - drawH) / 2f
                canvas.drawImageRect(
                    thumbImage,
                    Rect.makeWH(imgW, imgH),
                    Rect.makeXYWH(offsetX, offsetY, drawW, drawH)
                )
                canvas.restore()
            } else {
                // Placeholder box when image fails to load
                canvas.drawRRect(
                    RRect.makeLTRB(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize, thumbRadius),
                    paint(0xFFE0E0E0.toInt())
                )
            }
        }

        // ---- Text/details column (after thumbnail + spacer, or after rank box + spacer) ----
        val thumbSpacer = scoreThumbnailAfterSpacing
        val textOffset: Float = if (hasIllustration) {
            pad + scoreRankBoxSize + rankSpacer + thumbSize + thumbSpacer
        } else {
            pad + scoreRankBoxSize + rankSpacer
        }

        val nameStyle = textStyle(28f, bold = true)
        val namePaint = paint(0xFF1A1A2E.toInt())
        val maxNameWidth = cardWidth - textOffset - 100f
        val songName = truncateText(record.songName, nameStyle, namePaint, maxNameWidth)
        drawText(canvas, songName, x + textOffset, y + 84f, nameStyle, namePaint)

        val diffLabelText = "${B30ImageSpec.difficultyLabel(record.difficulty)} ${B30ImageSpec.formatChartConstant(record.chartConstant)}"
        val diffLabelStyle = textStyle(22f, bold = true)
        val diffLabelW = measureTextWidth(diffLabelText, diffLabelStyle, paint(0xFFFFFFFF.toInt()))
        val diffBadgeW = diffLabelW + 16f
        val diffBadgeH = 32f
        val diffBadgeY = y + 104f - diffBadgeH + 6f
        canvas.drawRRect(
            RRect.makeLTRB(x + textOffset, diffBadgeY, x + textOffset + diffBadgeW, diffBadgeY + diffBadgeH, 5f),
            paint(diffColor)
        )
        drawText(canvas, diffLabelText, x + textOffset + (diffBadgeW - diffLabelW) / 2f, diffBadgeY + 24f, diffLabelStyle, paint(0xFFFFFFFF.toInt()))

        var badgeRightEdge = x + textOffset + diffBadgeW

        if (record.isFullCombo || record.isPhi) {
            val badgeLabelStyle = textStyle(22f, bold = true)
            badgeRightEdge = drawFcPhiBadge(canvas, badgeRightEdge, diffBadgeY, 32f, record, badgeLabelStyle)
        }

        drawText(canvas, B30ImageSpec.formatScore(record.score), x + textOffset, y + 160f, textStyle(28f, bold = true), paint(0xFF1A1A2E.toInt()))
        drawText(canvas, B30ImageSpec.formatAccuracy(record.accuracy), x + textOffset, y + 196f, textStyle(24f), paint(0xFF6B7280.toInt()))

        val rksStyle = textStyle(32f, bold = true)
        val rksPaint = paint(0xFF2563EB.toInt())
        val rksText = B30ImageSpec.formatRks(record.rks)
        val rksW = measureTextWidth(rksText, rksStyle, rksPaint)
        drawText(canvas, rksText, x + cardWidth - pad - rksW, y + cardHeight - 24f, rksStyle, rksPaint)
    }

    private fun drawFcPhiBadge(
        canvas: org.jetbrains.skia.Canvas,
        startX: Float,
        badgeY: Float,
        badgeH: Float,
        record: BestRecord,
        labelStyle: TextStyleSpec
    ): Float {
        var edge = startX
        if (record.isFullCombo) {
            edge = drawOneBadge(canvas, edge, badgeY, badgeH, "FC", 0xFF4FC3F7.toInt(), 0xFFFFFFFF.toInt(), labelStyle)
        }
        if (record.isPhi) {
            edge = drawOneBadge(canvas, edge, badgeY, badgeH, "\u03C6", 0xFFFFD54F.toInt(), 0xFF5D4037.toInt(), labelStyle)
        }
        return edge
    }

    private fun drawOneBadge(
        canvas: org.jetbrains.skia.Canvas,
        x: Float,
        y: Float,
        h: Float,
        label: String,
        bg: Int,
        textColor: Int,
        labelStyle: TextStyleSpec
    ): Float {
        val labelW = measureTextWidth(label, labelStyle, paint(textColor))
        val badgeW = labelW + 14f
        val badgeX = x + 10f
        canvas.drawRRect(
            RRect.makeLTRB(badgeX, y, badgeX + badgeW, y + h, 5f),
            paint(bg)
        )
        drawText(canvas, label, badgeX + (badgeW - labelW) / 2f, y + 24f, labelStyle, paint(textColor))
        return badgeX + badgeW
    }

    private fun drawFooter(canvas: org.jetbrains.skia.Canvas, height: Int, dateText: String) {
        val footerY = height - pagePadding - footerHeight + footerTextOffset

        val footerStyle = textStyle(28f, bold = true)
        val footerPaint = paint(0xFF6B7280.toInt())

        drawText(canvas, "Generated by Phi Tracker", pagePadding, footerY, footerStyle, footerPaint)

        val rightW = measureTextWidth(dateText, footerStyle, footerPaint)
        drawText(canvas, dateText, contentWidth - pagePadding - rightW, footerY, footerStyle, footerPaint)
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
        fallbackCache[key]?.let { return it }

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
        while (truncated.isNotEmpty() && measureTextWidth("$truncated...", style, paint) > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated..."
    }
}
