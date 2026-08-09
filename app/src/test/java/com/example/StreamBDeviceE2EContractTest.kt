package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBDeviceE2EContractTest {
    private val checklistPath = "../docs/STREAM_B_DEVICE_E2E.md"

    @Test
    fun deviceChecklistCoversAllPrimaryCustomerSurfaces() {
        val checklist = File(checklistPath).readText()

        listOf(
            "## Home",
            "## Bills",
            "## Savings",
            "## Profile / privacy / preferences",
            "## Navigation / accessibility",
            "## Acceptance rule"
        ).forEach { section ->
            assertTrue("Device E2E checklist lost required section: $section", checklist.contains(section))
        }
    }

    @Test
    fun deviceChecklistRequiresCustomerSafeAuthAndGmailErrors() {
        val checklist = File(checklistPath).readText()

        listOf(
            "google_web_client_id",
            "Firebase/OAuth",
            "gmail.readonly",
            "scope",
            "exception"
        ).forEach { technicalTerm ->
            assertTrue(
                "Device E2E must explicitly verify that $technicalTerm is not customer-visible",
                checklist.contains(technicalTerm)
            )
        }
    }

    @Test
    fun deviceChecklistPreservesTruthfulnessExactOfferAndSavingsSemantics() {
        val checklist = File(checklistPath).readText()

        assertTrue(checklist.contains("never shows `₪0` as verified savings"))
        assertTrue(checklist.contains("exact verified monthly/annual saving"))
        assertTrue(checklist.contains("dedicated savings-success semantic green"))
        assertTrue(checklist.contains("brand/action controls remain on the blue brand palette"))
        assertTrue(checklist.contains("bound to the displayed offer"))
        assertTrue(checklist.contains("savings_action_starting"))
        assertTrue(checklist.contains("savings_action_submitting"))
        assertTrue(checklist.contains("Repeated taps cannot create a second in-flight submission"))
    }

    @Test
    fun deviceChecklistCoversRecoveryConfirmationAndAccessibility() {
        val checklist = File(checklistPath).readText()

        listOf(
            "dashboard_retry_financial_home",
            "savings_retry_refresh",
            "confirm_delete_bill",
            "confirm_profile_sign_out",
            "confirm_disconnect_document_source",
            "discard_preferences_changes",
            "48dp minimum touch target"
        ).forEach { requirement ->
            assertTrue("Device E2E lost required interaction check: $requirement", checklist.contains(requirement))
        }
    }

    @Test
    fun checklistDoesNotAuthorizeAutomaticSwitching() {
        val checklist = File(checklistPath).readText()

        assertTrue(checklist.contains("does not imply an automatic provider switch"))
        assertFalse(checklist.contains("automatic provider switch is allowed"))
    }
}
