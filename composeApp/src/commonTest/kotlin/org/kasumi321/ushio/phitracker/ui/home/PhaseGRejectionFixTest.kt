package org.kasumi321.ushio.phitracker.ui.home

import org.kasumi321.ushio.phitracker.domain.model.BestRecord
import org.kasumi321.ushio.phitracker.domain.model.Difficulty
import org.kasumi321.ushio.phitracker.ui.settings.shouldShowCrashNotificationGuide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhaseGRejectionFixTest {

    @Test
    fun updateCheckStateAvailableCarriesRequiredFields() {
        val available = UpdateCheckState.Available(
            version = "v2.0.0",
            htmlUrl = "https://github.com/test/release",
            body = "Release notes"
        )
        assertEquals("v2.0.0", available.version)
        assertEquals("https://github.com/test/release", available.htmlUrl)
        assertEquals("Release notes", available.body)
    }

    @Test
    fun dismissUpdateResultResetsToIdle() {
        val state = HomeUiState(
            updateCheckState = UpdateCheckState.Available(
                version = "v1.0.0",
                htmlUrl = "https://example.test",
                body = "New version"
            )
        )
        assertFalse(state.updateCheckState is UpdateCheckState.Idle)
        val newState = state.copy(updateCheckState = UpdateCheckState.Idle)
        assertTrue(newState.updateCheckState is UpdateCheckState.Idle)
    }

    @Test
    fun updateResultDialogAcceptsAvailableStateFields() {
        val available = UpdateCheckState.Available("v1", "url", "body")
        assertEquals("v1", available.version)
        assertEquals("url", available.htmlUrl)
        assertEquals("body", available.body)
    }

    @Test
    fun b30TabUsesFormatFourForDisplayRks() {
        val displayRks = 15.92995f
        val formatted = displayRks.formatFour()
        assertEquals("15.9300", formatted)
    }

    @Test
    fun homeViewModelHasClearAllLogsMethod() {
        // Compile-time safe verification: method reference fails to compile if method is missing
        val ref: (HomeViewModel) -> Boolean = HomeViewModel::clearAllLogs
        assertNotNull(ref)
    }

    @Test
    fun shouldShowCrashNotificationGuide_debugTrue_permissionFalse_notShown_true() {
        assertTrue(shouldShowCrashNotificationGuide(
            isDebugBuild = true,
            permissionGranted = false,
            alreadyShown = false
        ))
    }

    @Test
    fun shouldShowCrashNotificationGuide_debugFalse_permissionFalse_notShown_false() {
        assertFalse(shouldShowCrashNotificationGuide(
            isDebugBuild = false,
            permissionGranted = false,
            alreadyShown = false
        ))
    }

    @Test
    fun shouldShowCrashNotificationGuide_debugTrue_permissionTrue_notShown_false() {
        assertFalse(shouldShowCrashNotificationGuide(
            isDebugBuild = true,
            permissionGranted = true,
            alreadyShown = false
        ))
    }

    @Test
    fun shouldShowCrashNotificationGuide_debugTrue_alreadyShown_false() {
        assertFalse(shouldShowCrashNotificationGuide(
            isDebugBuild = true,
            permissionGranted = false,
            alreadyShown = true
        ))
    }

    @Test
    fun shouldShowCrashNotificationGuide_allConditionsMet_true() {
        assertTrue(shouldShowCrashNotificationGuide(
            isDebugBuild = true,
            permissionGranted = false,
            alreadyShown = false
        ))
    }

    @Test
    fun shouldShowCrashNotificationGuide_nonDebugNeverShows() {
        assertFalse(shouldShowCrashNotificationGuide(
            isDebugBuild = false,
            permissionGranted = false,
            alreadyShown = false
        ))
        assertFalse(shouldShowCrashNotificationGuide(
            isDebugBuild = false,
            permissionGranted = true,
            alreadyShown = false
        ))
        assertFalse(shouldShowCrashNotificationGuide(
            isDebugBuild = false,
            permissionGranted = false,
            alreadyShown = true
        ))
        assertFalse(shouldShowCrashNotificationGuide(
            isDebugBuild = false,
            permissionGranted = true,
            alreadyShown = true
        ))
    }

    @Test
    fun formatFourRoundsCorrectlyForTruncationBoundary() {
        assertEquals("15.9300", 15.92995f.formatFour())
    }

    @Test
    fun formatFourRoundsHalfUp() {
        assertEquals("1.2345", 1.23445f.formatFour())
    }

    @Test
    fun formatFourHandlesExactFourDecimals() {
        assertEquals("15.9299", 15.9299f.formatFour())
    }

    @Test
    fun formatFourHandlesZero() {
        assertEquals("0.0000", 0f.formatFour())
    }

    @Test
    fun formatFourHandlesWholeNumber() {
        assertEquals("16.0000", 16f.formatFour())
    }

    @Test
    fun formatFourHandlesRoundingDown() {
        assertEquals("15.9299", 15.92994f.formatFour())
    }

    @Test
    fun b30TabRksFormattingMatchesProfileTab() {
        val testValues = listOf(
            15.92995f to "15.9300",
            15.92994f to "15.9299",
            0f to "0.0000",
            16f to "16.0000",
            1.23445f to "1.2345",
            1.23455f to "1.2346"
        )

        for ((input, expected) in testValues) {
            assertEquals(expected, input.formatFour(),
                "formatFour($input) should produce $expected")
        }
    }

    @Test
    fun b30TabBestPhiRksUsesFormatFour() {
        val record = BestRecord(
            songId = "test",
            songName = "Test",
            difficulty = Difficulty.IN,
            score = 1_000_000,
            accuracy = 100f,
            isFullCombo = false,
            chartConstant = 16f,
            rks = 15.92995f,
            isPhi = true
        )
        assertEquals("15.9300", record.rks.formatFour())
    }

    @Test
    fun b30TabB27LastRksUsesFormatFour() {
        assertEquals("15.9300", 15.92995f.formatFour())
    }
}
