package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.kasumi321.ushio.phitracker.domain.model.B30TagAnalysis
import org.kasumi321.ushio.phitracker.domain.model.CategoryScore
import org.kasumi321.ushio.phitracker.domain.model.TagScore

/**
 * Radar chart of the weighted equivalent RKS per tag category, shared by
 * the B30 tab and the export image. Radius is normalized against
 * 1.25x the larger of the category maximum and the B30 average RKS.
 * Category labels carry their score beneath the name, like phi-plugin's
 * tag-radar-score in the b19 analysis panel.
 */
@Composable
fun TagRadarChart(
    categories: List<CategoryScore>,
    averageRks: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    // lineHeight must shrink along with fontSize: keeping labelSmall's 16sp
    // line height would leave several sp of invisible padding inside every
    // measured label and break the vertical rhythm of the name/score stack.
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp)
) {
    if (categories.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        // dp-based strokes so the export render at B30ExportSpec.DENSITY and
        // the on-screen tab keep the same relative line weight.
        val hairline = with(density) { 0.4.dp.toPx() }
        val outline = with(density) { 1.dp.toPx() }
        val count = categories.size
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.72f
        val maxRks = (categories.maxOf { it.rks }.coerceAtLeast(averageRks)) * 1.25f

        fun vertexAt(index: Int, fraction: Float): Offset {
            val angle = -Math.PI / 2 + 2 * Math.PI * index / count
            return Offset(
                x = center.x + (radius * fraction * cos(angle)).toFloat(),
                y = center.y + (radius * fraction * sin(angle)).toFloat()
            )
        }

        // Grid rings at 1/3, 2/3 and full scale
        listOf(1f / 3, 2f / 3, 1f).forEach { fraction ->
            val path = Path().apply {
                moveTo(vertexAt(0, fraction).x, vertexAt(0, fraction).y)
                for (i in 1 until count) {
                    val v = vertexAt(i, fraction)
                    lineTo(v.x, v.y)
                }
                close()
            }
            drawPath(path, color = gridColor, style = Stroke(width = hairline))
        }

        // Axes, category labels and per-category scores
        categories.forEachIndexed { index, category ->
            val tip = vertexAt(index, 1f)
            drawLine(color = gridColor, start = center, end = tip, strokeWidth = hairline)
            val labelPos = vertexAt(index, 1.18f)
            val nameLayout = textMeasurer.measure(category.name, style = labelStyle.copy(color = labelColor))
            val scoreLayout = textMeasurer.measure(
                "%.2f".format(category.rks),
                style = labelStyle.copy(color = labelColor)
            )
            val labelGap = with(density) { 2.dp.toPx() }
            val stackHeight = nameLayout.size.height + labelGap + scoreLayout.size.height
            // Clamp the stack as a whole, then place the score relative to the
            // clamped name top: clamping both texts independently would squash
            // the name/score gap for labels near the canvas edges.
            val stackTop = (labelPos.y - stackHeight / 2f)
                .coerceIn(0f, (size.height - stackHeight).coerceAtLeast(0f))
            // In the export card the canvas can be narrower than a measured
            // label; coerceIn throws when max < min, so clamp the upper bound.
            fun clampedX(left: Float, width: Int): Float =
                left.coerceIn(0f, (size.width - width).coerceAtLeast(0f))
            drawText(
                nameLayout,
                topLeft = Offset(
                    x = clampedX(labelPos.x - nameLayout.size.width / 2f, nameLayout.size.width),
                    y = stackTop
                )
            )
            drawText(
                scoreLayout,
                topLeft = Offset(
                    x = clampedX(labelPos.x - scoreLayout.size.width / 2f, scoreLayout.size.width),
                    y = (stackTop + nameLayout.size.height + labelGap)
                        .coerceIn(0f, (size.height - scoreLayout.size.height).coerceAtLeast(0f))
                )
            )
        }

        // Data polygon
        val dataPath = Path().apply {
            categories.forEachIndexed { index, category ->
                val fraction = (category.rks / maxRks).toDouble().coerceIn(0.0, 1.0).toFloat()
                val v = vertexAt(index, fraction)
                if (index == 0) moveTo(v.x, v.y) else lineTo(v.x, v.y)
            }
            close()
        }
        drawPath(dataPath, color = lineColor.copy(alpha = 0.25f))
        drawPath(dataPath, color = lineColor, style = Stroke(width = outline))
    }
}

/** Side-by-side "strong" and "weak" tag ranking columns. */
@Composable
fun B30TagStrongWeakColumns(
    analysis: B30TagAnalysis,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.4.dp)) {
        TagScoreColumn(
            title = "擅长",
            titleColor = MaterialTheme.colorScheme.primary,
            scores = analysis.strong,
            modifier = Modifier.weight(1f)
        )
        TagScoreColumn(
            title = "薄弱",
            titleColor = MaterialTheme.colorScheme.tertiary,
            scores = analysis.weak,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TagScoreColumn(
    title: String,
    titleColor: Color,
    scores: List<TagScore>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        if (scores.isEmpty()) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            scores.forEachIndexed { index, score ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        modifier = Modifier.width(16.dp)
                    )
                    Text(
                        text = score.name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "%.2f".format(score.rks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Panel header of the chart-tag analysis, matching phi-plugin's b19
 * "CHART PROFILE / 谱面标签能力" panel title with the effective vote
 * count on the right.
 */
@Composable
fun B30TagAnalysisPanelHeader(
    totalVotes: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "CHART PROFILE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "谱面标签能力",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "有效票 $totalVotes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Dim the charts under a centered hint overlay when the community vote
 * volume is insufficient, like phi-plugin's is-insufficient panel; passes
 * content through untouched otherwise. Used by the export image.
 */
@Composable
fun ChartTagInsufficientScrim(
    insufficient: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Box(modifier = Modifier.alpha(if (insufficient) 0.25f else 1f)) {
            content()
        }
        if (insufficient) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "可用谱面标签统计量不足，结果仅供参考。欢迎到曲目详情为谱面投票，帮助完善社区标签。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** Compact analysis body used by the collapsible B30 tab section. */
@Composable
fun B30TagAnalysisContent(
    analysis: B30TagAnalysis,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (analysis.insufficient) {
            Text(
                text = "标签数据不足，统计结果仅供参考。欢迎到曲目详情为谱面投票，帮助完善社区标签。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TagRadarChart(
            categories = analysis.categories,
            averageRks = analysis.averageRks,
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        B30TagStrongWeakColumns(analysis = analysis, modifier = Modifier.fillMaxWidth())
    }
}
