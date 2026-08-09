package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBDeviceEvidenceContractTest {
    private val evidencePath = "../docs/STREAM_B_DEVICE_EVIDENCE_TEMPLATE.md"

    @Test
    fun evidenceTemplateKeepsSameShaReleaseChain() {
        val evidence = File(evidencePath).readText()

        listOf(
            "Stream A baseline SHA",
            "integrated Stream B SHA",
            "CI run ID / URL",
            "Staging APK artifact name",
            "APK signing verification result",
            "CI green SHA",
            "APK source SHA",
            "Device-tested SHA"
        ).forEach { requirement ->
            assertTrue("Device evidence lost same-SHA requirement: $requirement", evidence.contains(requirement))
        }
    }

    @Test
    fun evidenceTemplateCoversEveryPrimaryCustomerJourney() {
        val evidence = File(evidencePath).readText()

        listOf(
            "Initial connection / Google authorization",
            "Dashboard / verified savings hero",
            "Bills list / manual add / category selection",
            "Savings verified offer presentation",
            "ACTION_STARTED → explicit consent → exact offer",
            "Savings submitting / double-submit prevention",
            "Profile sign-out confirmation",
            "Privacy & Connections / disconnect confirmation",
            "Savings preferences / save / unsaved-changes protection",
            "RTL bottom navigation / accessibility"
        ).forEach { journey ->
            assertTrue("Device evidence lost customer journey: $journey", evidence.contains(journey))
        }
    }

    @Test
    fun evidenceTemplateKeepsTruthfulnessAndPrivacyChecks() {
        val evidence = File(evidencePath).readText()

        listOf(
            "No `₪0` is presented as verified savings",
            "No annual savings value is synthesized",
            "savings-success green",
            "Authorization failures do not expose client IDs",
            "No automatic provider switch",
            "explicit consent for the exact displayed offer"
        ).forEach { rule ->
            assertTrue("Device evidence lost trust rule: $rule", evidence.contains(rule))
        }
    }
}
