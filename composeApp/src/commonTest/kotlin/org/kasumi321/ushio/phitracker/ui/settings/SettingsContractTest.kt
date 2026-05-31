package org.kasumi321.ushio.phitracker.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract tests for Settings UI constants.
 * Ensures the Overflow slider range and steps stay in sync between UI and data layer.
 */
class SettingsContractTest {

    @Test
    fun overflowMaxIs30() {
        assertEquals(30, SettingsConstants.OVERFLOW_COUNT_MAX,
            "Overflow slider max must be 30 per Phase G spec")
    }

    @Test
    fun overflowStepsIs28() {
        assertEquals(28, SettingsConstants.OVERFLOW_SLIDER_STEPS,
            "Overflow slider steps must be 28 (max 30 - min 1 - 1) per Phase G spec")
    }

    @Test
    fun overflowMinIs1() {
        assertEquals(1, SettingsConstants.OVERFLOW_COUNT_MIN,
            "Overflow slider min must be 1")
    }

    @Test
    fun stepsDerivedCorrectly() {
        val expectedSteps = SettingsConstants.OVERFLOW_COUNT_MAX - SettingsConstants.OVERFLOW_COUNT_MIN - 1
        assertEquals(expectedSteps, SettingsConstants.OVERFLOW_SLIDER_STEPS,
            "OVERFLOW_SLIDER_STEPS must equal max - min - 1")
    }
}
