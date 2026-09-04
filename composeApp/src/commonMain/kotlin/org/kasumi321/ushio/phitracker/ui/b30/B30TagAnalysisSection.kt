package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
 */
@Composable
fun TagRadarChart(
    categories: List<CategoryScore>,
    averageRks: Float,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    if (categories.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
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
            drawPath(path, color = gridColor, style = Stroke(width = 1f))
        }

        // Axes and category labels
        categories.forEachIndexed { index, category ->
            val tip = vertexAt(index, 1f)
            drawLine(color = gridColor, start = center, end = tip, strokeWidth = 1f)
            val labelPos = vertexAt(index, 1.18f)
            val label = textMeasurer.measure(category.name, style = labelStyle.copy(color = labelColor))
            drawText(
                label,
                topLeft = Offset(
                    x = (labelPos.x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width),
                    y = (labelPos.y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)
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
        drawPath(dataPath, color = lineColor, style = Stroke(width = 2.5f))
    }
}

/** Side-by-side "strong" and "weak" tag ranking columns. */
@Composable
fun B30TagStrongWeakColumns(
    analysis: B30TagAnalysis,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
            scores.forEach { score ->
                Row(verticalAlignment = Alignment.CenterVertically) {
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
