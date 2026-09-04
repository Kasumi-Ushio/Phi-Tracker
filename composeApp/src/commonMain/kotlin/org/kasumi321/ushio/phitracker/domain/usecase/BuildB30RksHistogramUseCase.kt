package org.kasumi321.ushio.phitracker.domain.usecase

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.kasumi321.ushio.phitracker.domain.model.B30RksHistogram
import org.kasumi321.ushio.phitracker.domain.model.B30RksHistogramSlot
import org.kasumi321.ushio.phitracker.domain.model.B30RksHistogramTick
import org.kasumi321.ushio.phitracker.domain.model.BestRecord

/**
 * Equivalent single-chart RKS histogram over the effective B30 slots
 * (P1-P3 white bars, B1-B27 blue bars in phi-plugin's b19 image), ported
 * from phi-plugin's model/game/b30Analysis.js buildRksHistogram: nice-step
 * value domain, per-slot normalized bar heights and the average marker
 * position.
 */
class BuildB30RksHistogramUseCase {
    operator fun invoke(
        records: List<BestRecord>,
        targetTickCount: Int = DEFAULT_TARGET_TICK_COUNT
    ): B30RksHistogram? {
        val valid = records.filter { it.rks.isFinite() }
        if (valid.isEmpty()) return null

        val values = valid.map { it.rks.toDouble() }
        val minimum = values.min()
        val maximum = values.max()
        val step = niceAxisStep(maxOf(maximum - minimum, 0.2) / targetTickCount)
        val domainMin = floor((minimum - step * 0.1) / step) * step
        var domainMax = ceil((maximum + step * 0.1) / step) * step
        if (domainMax <= domainMin) domainMax = domainMin + step
        val domainRange = domainMax - domainMin

        val tickCount = (domainRange / step).roundToInt()
        val ticks = (0..tickCount).map { index ->
            val value = domainMin + index * step
            B30RksHistogramTick(
                value = value.toFloat(),
                label = formatTick(value),
                fraction = (index.toDouble() / tickCount).toFloat()
            )
        }

        val phiCounter = intArrayOf(0)
        val bestCounter = intArrayOf(0)
        val slots = valid.map { record ->
            val label = if (record.isPhi) {
                "P${++phiCounter[0]}"
            } else {
                "B${++bestCounter[0]}"
            }
            B30RksHistogramSlot(
                label = label,
                rks = record.rks,
                isPhi = record.isPhi,
                height = (((record.rks - domainMin) / domainRange) * 100.0)
                    .coerceIn(0.0, 100.0).toFloat()
            )
        }

        val average = values.sum() / values.size
        return B30RksHistogram(
            slots = slots,
            ticks = ticks,
            average = average.toFloat(),
            averagePosition = (((average - domainMin) / domainRange) * 100.0)
                .coerceIn(0.0, 100.0).toFloat(),
            count = valid.size,
            domainMin = domainMin.toFloat(),
            domainMax = domainMax.toFloat()
        )
    }

    private fun niceAxisStep(value: Double): Double {
        val candidates = listOf(0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 1.0)
        return candidates.firstOrNull { it >= value } ?: ceil(value)
    }

    private fun formatTick(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return rounded.toString()
    }

    private companion object {
        const val DEFAULT_TARGET_TICK_COUNT = 4
    }
}
