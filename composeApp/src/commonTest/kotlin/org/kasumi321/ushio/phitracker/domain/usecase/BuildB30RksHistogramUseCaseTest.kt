package org.kasumi321.ushio.phitracker.domain.usecase

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuildB30RksHistogramUseCaseTest {

    private fun makeRecord(rks: Float, isPhi: Boolean = false) = BestRecord(
        songId = "song_$rks",
        songName = "Song",
        difficulty = Difficulty.IN,
        score = 1_000_000,
        accuracy = 100f,
        isFullCombo = true,
        chartConstant = 15f,
        rks = rks,
        isPhi = isPhi
    )

    private val useCase = BuildB30RksHistogramUseCase()

    @Test
    fun emptyRecordsProduceNullHistogram() {
        assertNull(useCase(emptyList()))
    }

    @Test
    fun nonFiniteRksValuesAreExcluded() {
        val histogram = useCase(
            listOf(
                makeRecord(Float.NaN),
                makeRecord(15f),
                makeRecord(16f)
            )
        )

        assertNotNull(histogram)
        assertEquals(2, histogram.count)
        assertEquals(2, histogram.slots.size)
    }

    @Test
    fun valueDomainUsesNiceStepsCoveringAllSlots() {
        val histogram = useCase(listOf(makeRecord(15f), makeRecord(16f)))

        assertNotNull(histogram)
        // range 1.0 / 4 ticks = 0.25 -> the 0.25 nice-step candidate
        assertEquals(14.75f, histogram.domainMin, 0.001f)
        assertEquals(16.25f, histogram.domainMax, 0.001f)
        assertEquals(7, histogram.ticks.size)
        assertEquals(14.75f, histogram.ticks.first().value, 0.001f)
        assertEquals(16.25f, histogram.ticks.last().value, 0.001f)
        assertEquals(0f, histogram.ticks.first().fraction)
        assertEquals(1f, histogram.ticks.last().fraction)
        // Bar heights are normalized against the value domain
        assertEquals(16.67f, histogram.slots[0].height, 0.01f)
        assertEquals(83.33f, histogram.slots[1].height, 0.01f)
    }

    @Test
    fun averageAndItsMarkerPositionAreComputed() {
        val histogram = useCase(listOf(makeRecord(15f), makeRecord(16f)))

        assertNotNull(histogram)
        assertEquals(15.5f, histogram.average, 0.001f)
        assertEquals(50f, histogram.averagePosition, 0.01f)
    }

    @Test
    fun identicalRksValuesStillProduceADomain() {
        val histogram = useCase(listOf(makeRecord(15.5f), makeRecord(15.5f)))

        assertNotNull(histogram)
        assertTrue(histogram.domainMax > histogram.domainMin)
        assertEquals(50f, histogram.slots[0].height, 0.01f)
        assertEquals(50f, histogram.averagePosition, 0.01f)
    }

    @Test
    fun slotLabelsNumberPhiAndBestSlotsIndependentlyInInputOrder() {
        val histogram = useCase(
            listOf(
                makeRecord(16f, isPhi = true),
                makeRecord(15f),
                makeRecord(15.5f, isPhi = true),
                makeRecord(14f)
            )
        )

        assertNotNull(histogram)
        assertEquals(listOf("P1", "B1", "P2", "B2"), histogram.slots.map { it.label })
        assertEquals(
            listOf(true, false, true, false),
            histogram.slots.map { it.isPhi }
        )
    }
}
