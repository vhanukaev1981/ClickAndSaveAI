package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBReleaseReadinessContractTest {
    @Test
    fun requiredAcceptanceAndIntegrationArtifactsExist() {
        listOf(
            "../docs/STREAM_B_ACCEPTANCE_MATRIX.md",
            "../docs/STREAM_B_DEVICE_E2E.md",
            "../docs/STREAM_B_INTEGRATION_PLAN.md",
            "../docs/STREAM_B_RELEASE_GATE.md",
            "../docs/STREAM_B_DEVICE_EVIDENCE_TEMPLATE.md",
            "../docs/STREAM_B_MOTION_CONTRACT.md",
            "../docs/STREAM_B_P0_GAP_MAP.md",
            "../docs/STREAM_B_NORTH_STAR_STATUS.md",
            "../docs/STREAM_B_ONBOARDING_CONTRACT.md",
            "../docs/STREAM_B_PROVIDER_HANDOFF_CONTRACT.md",
            "../docs/STREAM_B_BILLS_PAYMENT_HANDOFF_CONTRACT.md"
        ).forEach { path ->
            assertTrue("Missing Stream B readiness artifact: $path", File(path).isFile)
        }
    }

    @Test
    fun acceptanceMatrixCoversEveryPrimaryCustomerSurfaceAndThemeContract() {
        val matrix = File("../docs/STREAM_B_ACCEPTANCE_MATRIX.md").readText()
        listOf(
            "Dashboard",
            "Progress",
            "Motion",
            "Bills",
            "payment handoff",
            "Savings",
            "Provider handoff",
            "Profile",
            "Privacy",
            "Preferences",
            "Navigation",
            "Accessibility",
            "Theme",
            "Typography",
            "Customer copy",
            "Consent",
            "Evidence"
        ).forEach { area ->
            assertTrue("Acceptance matrix is missing $area", matrix.contains(area))
        }
    }

    @Test
    fun deviceAcceptanceRequiresExactGreenHeadAndRealDeviceValidation() {
        val e2e = File("../docs/STREAM_B_DEVICE_E2E.md").readText()
        val integration = File("../docs/STREAM_B_INTEGRATION_PLAN.md").readText()
        val releaseGate = File("../docs/STREAM_B_RELEASE_GATE.md").readText()
        val evidence = File("../docs/STREAM_B_DEVICE_EVIDENCE_TEMPLATE.md").readText()
        val motion = File("../docs/STREAM_B_MOTION_CONTRACT.md").readText()

        assertTrue(e2e.contains("real-device E2E acceptance"))
        assertTrue(e2e.contains("Acceptance rule"))
        assertTrue(integration.contains("CI"))
        assertTrue(integration.contains("APK"))
        assertTrue(integration.contains("Stream A"))
        assertTrue(releaseGate.contains("exact green SHA"))
        assertTrue(releaseGate.contains("real Android device"))
        assertTrue(releaseGate.contains("Historical green CI does not validate a newer HEAD"))
        assertTrue(releaseGate.contains("dedicated savings-success green semantic"))
        assertTrue(releaseGate.contains("Authorization failures must remain customer-safe"))
        assertTrue(releaseGate.contains("StreamBDeviceE2EContractTest"))
        assertTrue(evidence.contains("CI green SHA"))
        assertTrue(evidence.contains("APK source SHA"))
        assertTrue(evidence.contains("Device-tested SHA"))
        assertTrue(motion.contains("does not calculate a completion percentage"))
        assertTrue(motion.contains("does not advance a stage on a timer"))
    }

    @Test
    fun automatedGuardSuiteRetainsCoreProductContracts() {
        listOf(
            "CustomerPresentationPolicyTest.kt",
            "CustomerAuthCopySanitizationTest.kt",
            "CustomerVisibleCopyGuardTest.kt",
            "DashboardProductContractTest.kt",
            "FinancialThemeContractTest.kt",
            "TruthfulProgressPresentationPolicyTest.kt",
            "ProviderHandoffPresentationPolicyTest.kt",
            "PaymentHandoffPresentationPolicyTest.kt",
            "StreamBNorthStarContractTest.kt",
            "StreamBFinancialSurfaceContractTest.kt",
            "ProfilePrivacyProductContractTest.kt",
            "SettingsProductContractTest.kt",
            "StreamBAccessibilityContractTest.kt",
            "StreamBConsentPrivacyContractTest.kt",
            "StreamBBoundaryContractTest.kt",
            "StreamBManagerBlockerContractTest.kt",
            "StreamBPostRebaseContractTest.kt",
            "StreamBDeviceE2EContractTest.kt",
            "StreamBDeviceEvidenceContractTest.kt",
            "StreamBMotionContractTest.kt",
            "StreamBOnboardingContractTest.kt",
            "StreamBProviderHandoffContractTest.kt",
            "StreamBBillsPaymentHandoffContractTest.kt",
            "StreamBWorkstreamBoundaryContractTest.kt"
        ).forEach { fileName ->
            val file = File("src/test/java/com/example/$fileName")
            assertTrue("Missing Stream B automated guard: $fileName", file.isFile)
        }
    }
}
