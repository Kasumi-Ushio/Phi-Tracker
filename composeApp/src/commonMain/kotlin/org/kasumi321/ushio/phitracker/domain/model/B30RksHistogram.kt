package org.kasumi321.ushio.phitracker.domain.model

/**
 * RKS distribution histogram over the effective B30 slots (P1-P3 + B1-B27),
 * shared by the B30 tab UI and the export image (KMP-safe, no UI types).
 * All fractions are in the 0..1 range relative to the value domain.
 */
data class B30RksHistogram(
    val slots: List<B30RksHistogramSlot>,
    val ticks: List<B30RksHistogramTick>,
    val average: Float,
    val averagePosition: Float,
    val count: Int,
    val domainMin: Float,
    val domainMax: Float
)

/** A single histogram bar: one effective B30 slot. */
data class B30RksHistogramSlot(
    val label: String,
    val rks: Float,
    val isPhi: Boolean,
    val height: Float
)

/** A y-axis tick of the histogram. */
data class B30RksHistogramTick(
    val value: Float,
    val label: String,
    val fraction: Float
)
