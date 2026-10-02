package org.kasumi321.ushio.phitracker.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartGateTest {

    @Test
    fun rerunRequestWinsEvenWithTokenAndRoutesToOnboarding() {
        val decision = decideStartGate(
            onboardingCompleted = true,
            rerunRequested = true,
            preloadDone = true,
            hasToken = true
        )

        assertEquals(Screen.Onboarding.route, decision.route)
        assertTrue(decision.consumeRerunRequest)
        assertFalse(decision.markOnboardingCompleted)
    }

    @Test
    fun freshInstallSeesWizardWithoutMarkingAnything() {
        val decision = decideStartGate(
            onboardingCompleted = false,
            rerunRequested = false,
            preloadDone = false,
            hasToken = false
        )

        assertEquals(Screen.Onboarding.route, decision.route)
        assertFalse(decision.consumeRerunRequest)
        assertFalse(decision.markOnboardingCompleted)
    }

    @Test
    fun upgradingTokenHolderIsSilentlyMarkedOnboardedAndGoesToLogin() {
        val decision = decideStartGate(
            onboardingCompleted = false,
            rerunRequested = false,
            preloadDone = true,
            hasToken = true
        )

        assertEquals(Screen.Login.route, decision.route)
        assertFalse(decision.consumeRerunRequest)
        assertTrue(decision.markOnboardingCompleted)
    }

    @Test
    fun upgradingGuestWithPreloadRecordIsSilentlyMarkedOnboardedAndGoesHome() {
        val decision = decideStartGate(
            onboardingCompleted = false,
            rerunRequested = false,
            preloadDone = true,
            hasToken = false
        )

        assertEquals(Screen.Home.route, decision.route)
        assertFalse(decision.consumeRerunRequest)
        assertTrue(decision.markOnboardingCompleted)
    }

    @Test
    fun onboardedTokenHolderGoesToLoginUntouched() {
        val decision = decideStartGate(
            onboardingCompleted = true,
            rerunRequested = false,
            preloadDone = true,
            hasToken = true
        )

        assertEquals(Screen.Login.route, decision.route)
        assertFalse(decision.consumeRerunRequest)
        assertFalse(decision.markOnboardingCompleted)
    }

    @Test
    fun onboardedGuestGoesHomeUntouched() {
        val decision = decideStartGate(
            onboardingCompleted = true,
            rerunRequested = false,
            preloadDone = true,
            hasToken = false
        )

        assertEquals(Screen.Home.route, decision.route)
        assertFalse(decision.consumeRerunRequest)
        assertFalse(decision.markOnboardingCompleted)
    }
}
