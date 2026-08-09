package com.example

import com.example.ui.CustomerPresentationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerPresentationPolicyTest {
    @Test
    fun zeroSavingsIsNeverPresentedAsVerifiedSaving() {
        assertNull(CustomerPresentationPolicy.verifiedSavingsLabel(0.0, 0.0))
        assertNull(CustomerPresentationPolicy.verifiedSavingsLabel(null, null))
        assertNull(CustomerPresentationPolicy.verifiedSavingsLabel(Double.NaN, 100.0))
    }

    @Test
    fun positiveMonthlyAndAnnualSavingProduceCustomerFacingLabel() {
        val label = CustomerPresentationPolicy.verifiedSavingsLabel(25.0, 300.0)
        assertTrue(label.orEmpty().contains("₪25.00"))
        assertTrue(label.orEmpty().contains("₪300.00"))
    }

    @Test
    fun missingAnnualSavingShowsVerifiedMonthlyOnly() {
        val label = CustomerPresentationPolicy.verifiedSavingsLabel(25.0, null)
        assertEquals("אפשר לחסוך ₪25.00 בחודש", label)
        assertFalse(label.orEmpty().contains("₪300.00"))
        assertFalse(label.orEmpty().contains("בשנה"))
    }

    @Test
    fun nonPositiveAnnualSavingIsNotInventedFromMonthlySaving() {
        val label = CustomerPresentationPolicy.verifiedSavingsLabel(25.0, 0.0)
        assertEquals("אפשר לחסוך ₪25.00 בחודש", label)
    }

    @Test
    fun internalBackendTokensAreNeverShownToCustomer() {
        val samples = listOf(
            "GMAIL_READONLY",
            "provider_reference=abc",
            "Firebase App_Check failed",
            "backend exception",
            "lead_status=NEW",
            "dispatch_id=123",
            "commission=45",
            "attribution_id=xyz"
        )
        samples.forEach {
            assertEquals("המידע מתעדכן", CustomerPresentationPolicy.safeStatus(it))
        }
    }

    @Test
    fun pendingAndProcessingStatesUsePlainCustomerLanguage() {
        assertEquals("נמצא בבדיקה", CustomerPresentationPolicy.safeStatus("PENDING_VERIFICATION"))
        assertEquals("נמצא בבדיקה", CustomerPresentationPolicy.safeStatus("PROCESSING"))
    }

    @Test
    fun technicalErrorsAreSanitized() {
        val safe = CustomerPresentationPolicy.safeError("HTTP 500 FirebaseException token=secret")
        assertEquals("לא הצלחנו לעדכן כרגע. ננסה שוב אוטומטית.", safe)
        assertFalse(safe.uppercase().contains("FIREBASE"))
        assertFalse(safe.uppercase().contains("TOKEN"))
    }

    @Test
    fun customerSafeErrorsCanRemainSpecificButBounded() {
        val safe = CustomerPresentationPolicy.safeError("ההצעה כבר אינה זמינה")
        assertEquals("ההצעה כבר אינה זמינה", safe)
    }
}
