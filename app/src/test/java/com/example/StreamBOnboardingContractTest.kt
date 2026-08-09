package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBOnboardingContractTest {
    private val contractPath = "../docs/STREAM_B_ONBOARDING_CONTRACT.md"

    @Test
    fun onboardingBuildsTrustBeforePermissionAndValueReveal() {
        val contract = File(contractPath).readText()

        listOf(
            "Privacy explanation before permission",
            "Google sign-in",
            "Gmail read-only purpose",
            "First scan/import experience driven only by Core-provided states",
            "If a verified saving exists, reveal it with Verified Green",
            "never ₪0 as failure"
        ).forEach { requirement ->
            assertTrue("Onboarding lost P0 requirement: $requirement", contract.contains(requirement))
        }
    }

    @Test
    fun onboardingNeverFakesProgressOrPermissionSuccess() {
        val contract = File(contractPath).readText()

        listOf(
            "No percentage, countdown or timer may advance the state",
            "must not simulate a staged live scan",
            "Cancelling or failing authorization must not visually advance the journey",
            "does not authorize a provider switch, payment, contract or cancellation"
        ).forEach { rule ->
            assertTrue("Onboarding lost trust rule: $rule", contract.contains(rule))
        }
    }

    @Test
    fun onboardingWaitsForCoreStateContractBeforeLiveWiring() {
        val contract = File(contractPath).readText()

        assertTrue(contract.contains("Core exposure of explicit scan/import/analyze/compare/verify evidence states"))
        assertTrue(contract.contains("Stream B rebase onto the validated Stream A baseline"))
        assertTrue(contract.contains("do not fake them in Stream B"))
    }
}
