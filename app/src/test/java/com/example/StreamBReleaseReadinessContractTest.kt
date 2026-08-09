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
            "../docs/STREAM_B_RELEASE_GATE.md"
        ).forEach { path ->
            assertTrue("Missing Stream B readiness artifact: $path", File(path).isFile)
        }
    }

    @Test
    fun acceptanceMatrixCoversEveryPrimaryCustomerSurfaceAndThemeContract() {
        val matrix = File("../docs/STREAM_B_ACCEPTANCE_MATRIX.md").readText()
        listOf(
            "Dashboard",
            "Bills",
            "Savings",
            "Profile",
            "Privacy",
            "Preferences",
            "Navigation",
            "Accessibility",
            "Theme",
            "Typography",
            "Customer copy",
            "Consent"
        ).forEach { area ->
            assertTrue("Acceptance matrix is missing $area", matrix.contains(area))
        }
    }

    @Test
    fun deviceAcceptanceRequiresExactGreenHeadAndRealDeviceValidation() {
        val e2e = File("../docs/STREAM_B_DEVICE_E2E.md").readText()
        val integration = File("../docs/STREAM_B_INTEGRATION_PLAN.md").readText()
        val releaseGate = File("../docs/STREAM_B_RELEASE_GATE.md").readText()

        assertTrue(e2e.contains("real-device E2E acceptance"))
        assertTrue(e2e.contains("Acceptance rule"))
        assertTrue(integration.contains("CI"))
        assertTrue(integration.contains("APK"))
        assertTrue(integration.contains("Stream A"))
        assertTrue(releaseGate.contains("exact green SHA"))
        assertTrue(releaseGate.contains("real Android device"))
        assertTrue(releaseGate.contains("Historical green CI does not validate a newer HEAD"))
    }

    @Test
    fun automatedGuardSuiteRetainsCoreProductContracts() {
        listOf(
            "CustomerPresentationPolicyTest.kt",
            "CustomerVisibleCopyGuardTest.kt",
            "DashboardProductContractTest.kt",
            "FinancialThemeContractTest.kt",
            "StreamBFinancialSurfaceContractTest.kt",
            "ProfilePrivacyProductContractTest.kt",
            "SettingsProductContractTest.kt",
            "StreamBAccessibilityContractTest.kt",
            "StreamBConsentPrivacyContractTest.kt",
            "StreamBBoundaryContractTest.kt",
            "StreamBManagerBlockerContractTest.kt",
            "StreamBPostRebaseContractTest.kt",
            "StreamBDeviceE2EContractTest.kt",
            "StreamBWorkstreamBoundaryContractTest.kt"
        ).forEach { fileName ->
            val file = File("src/test/java/com/example/$fileName")
            assertTrue("Missing Stream B automated guard: $fileName", file.isFile)
        }
    }
}
