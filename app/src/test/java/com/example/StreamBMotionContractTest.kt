package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBMotionContractTest {
    private val motionPath = "../docs/STREAM_B_MOTION_CONTRACT.md"

    @Test
    fun motionContractRequiresBackendDrivenTruthfulStages() {
        val motion = File(motionPath).readText()

        listOf(
            "DETECTED",
            "CHECKED",
            "VERIFIED",
            "STILL_CHECKING",
            "does not calculate a completion percentage",
            "does not advance a stage on a timer"
        ).forEach { requirement ->
            assertTrue("Motion contract lost truthful-state requirement: $requirement", motion.contains(requirement))
        }
    }

    @Test
    fun motionContractProtectsVerificationAndAccessibility() {
        val motion = File(motionPath).readText()

        listOf(
            "Blue → Verified Green transition only after verified evidence exists",
            "never animate a fabricated zero or inferred amount",
            "Respect reduced-motion preferences",
            "animation as the only status signal",
            "claiming activation/conversion because a provider page was merely opened"
        ).forEach { rule ->
            assertTrue("Motion contract lost safety rule: $rule", motion.contains(rule))
        }
    }
}
