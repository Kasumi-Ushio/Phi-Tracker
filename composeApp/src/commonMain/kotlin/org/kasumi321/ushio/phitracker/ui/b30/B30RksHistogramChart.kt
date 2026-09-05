package org.kasumi321.ushio.phitracker.ui.b30

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.kasumi321.ushio.phitracker.domain.model.B30RksHistogram
import org.kasumi321.ushio.phitracker.ui.home.formatFour

/**
 * Equivalent single-chart RKS distribution of the effective B30 slots,
 * following phi-plugin's b19 histogram panel ("RKS DISTRIBUTION / 等效 RKS
 * 直方图"): phi slots draw solid bars, best slots draw faded bars of the
 * same hue, and a dashed line marks the slot average.
 */
@Composable
fun B30RksHistogramChart(
    histogram: B30RksHistogram,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    labelStyle: TextStyle = MaterialTheme.typography.labelSmall
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "RKS DISTRIBUTION",
            style = labelStyle,
            color = labelColor
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "等效 RKS 直方图",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // formatFour instead of String.format: this is commonMain code and
            // must also compile for the iOS targets.
            Text(
                text = "平均 RKS ${histogram.average.formatFour()}",
                style = labelStyle,
                color = labelColor
            )
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            // dp-based strokes so the export render at B30ExportSpec.DENSITY
            // keeps the same relative line weight as the preview.
            val hairline = with(density) { 0.4.dp.toPx() }
            val averageStroke = with(density) { 0.75.dp.toPx() }
            val plotPadding = with(density) { 3.dp.toPx() }
            val tickLayout = histogram.ticks.map { tick ->
                val textLayout = textMeasurer.measure(tick.label, style = labelStyle.copy(color = labelColor))
                tick to textLayout
            }
            val labelWidth = tickLayout.maxOfOrNull { it.second.size.width }?.toFloat() ?: 0f
            val labelHeight = tickLayout.maxOfOrNull { it.second.size.height }?.toFloat() ?: 0f
            val bottomPadding = labelHeight * 1.4f
            val plotLeft = labelWidth + plotPadding
            val plotRight = size.width
            val plotTop = 0f
            val plotBottom = size.height - bottomPadding
            if (plotRight <= plotLeft || plotBottom <= plotTop) return@Canvas
            val plotHeight = plotBottom - plotTop

            // Horizontal tick lines with labels on the left edge.
            tickLayout.forEach { (tick, textLayout) ->
                val y = plotBottom - tick.fraction * plotHeight
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = hairline
                )
                drawText(
                    textLayout,
                    topLeft = Offset(
                        x = (plotLeft - 8f - textLayout.size.width).coerceAtLeast(0f),
                        y = (y - textLayout.size.height / 2f).coerceIn(0f, size.height - textLayout.size.height)
                    )
                )
            }

            // Bars, one per effective slot, baseline on the bottom edge.
            if (histogram.slots.isNotEmpty()) {
                val slotWidth = (plotRight - plotLeft) / histogram.slots.size
                val barWidth = slotWidth * 0.62f
                histogram.slots.forEachIndexed { index, slot ->
                    val barHeight = slot.height / 100f * plotHeight
                    drawRect(
                        color = if (slot.isPhi) barColor else barColor.copy(alpha = 0.38f),
                        topLeft = Offset(
                            x = plotLeft + index * slotWidth + (slotWidth - barWidth) / 2f,
                            y = plotBottom - barHeight
                        ),
                        size = Size(barWidth, barHeight.coerceAtLeast(1f))
                    )
                }
            }

            // Average marker across the plot.
            val averageY = plotBottom - histogram.averagePosition / 100f * plotHeight
            drawLine(
                color = averageColor,
                start = Offset(plotLeft, averageY),
                end = Offset(plotRight, averageY),
                strokeWidth = averageStroke,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(
                        with(density) { 4.dp.toPx() },
                        with(density) { 3.dp.toPx() }
                    )
                )
            )
            val averageLabel = textMeasurer.measure("AVG", style = labelStyle.copy(color = averageColor))
            drawText(
                averageLabel,
                topLeft = Offset(
                    x = plotRight - averageLabel.size.width,
                    y = (averageY - averageLabel.size.height).coerceAtLeast(0f)
                )
            )

            // Domain range labels at the bottom corners.
            val minLabel = textMeasurer.measure(histogram.ticks.first().label, style = labelStyle.copy(color = labelColor))
            val maxLabel = textMeasurer.measure(histogram.ticks.last().label, style = labelStyle.copy(color = labelColor))
            drawText(minLabel, topLeft = Offset(plotLeft, size.height - minLabel.size.height))
            drawText(maxLabel, topLeft = Offset(plotRight - maxLabel.size.width, size.height - maxLabel.size.height))
        }
    }
}
