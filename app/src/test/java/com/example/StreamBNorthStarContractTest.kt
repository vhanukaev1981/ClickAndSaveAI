package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBNorthStarContractTest {
    private val gapMap = File("../docs/STREAM_B_P0_GAP_MAP.md")
    private val motion = File("../docs/STREAM_B_MOTION_CONTRACT.md")
    private val onboarding = File("../docs/STREAM_B_ONBOARDING_CONTRACT.md")
    private val handoff = File("../docs/STREAM_B_PROVIDER_HANDOFF_CONTRACT.md")
    private val payment = File("../docs/STREAM_B_BILLS_PAYMENT_HANDOFF_CONTRACT.md")

    @Test
    fun northStarRemainsTrustThenActionThenSavings() {
        val combined = listOf(gapMap, onboarding, handoff).joinToString("\n") { it.readText() }
        assertTrue(combined.contains("trust", ignoreCase = true))
        assertTrue(combined.contains("explicit consent", ignoreCase = true))
        assertTrue(combined.contains("verified saving", ignoreCase = true))
    }

    @Test
    fun productNeverFallsBackToAiTheatreOrFakeProgress() {
        val motionText = motion.readText()
        val onboardingText = onboarding.readText()
        assertTrue(motionText.contains("robot/mascot AI theatre"))
        assertTrue(motionText.contains("fake percentages"))
        assertTrue(onboardingText.contains("must not simulate a staged live scan"))
    }

    @Test
    fun currentPhaseNeverBecomesPaymentOrSwitchExecutionProduct() {
        val handoffText = handoff.readText()
        val paymentText = payment.readText()
        assertTrue(handoffText.contains("does not perform the provider switch itself"))
        assertTrue(paymentText.contains("does not process customer payments"))
        assertTrue(paymentText.contains("does not store card details"))
    }

    @Test
    fun fifthActivityAreaWaitsForMeaningfulCoreActivityData() {
        val map = gapMap.readText()
        assertTrue(map.contains("Activity primary area"))
        assertTrue(map.contains("do not add a fifth tab until meaningful Core activity states/data exist"))
    }
}
