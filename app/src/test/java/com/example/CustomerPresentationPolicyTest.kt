package com.example

import com.example.ui.CustomerPresentationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerPresentationPolicyTest {
    @Test
    fun zeroSavingsIsNeverPresentedAsVerifiedSaving() {
        assertNull(CustomerPresentationPolicy.verifiedSavingsLabel(0.0, 0.0))
        assertNull(CustomerPresentationPolicy.verifiedSavingsLabel(null, null))
    }

    @Test
    fun positiveMonthlySavingProducesCustomerFacingLabel() {
        val label = CustomerPresentationPolicy.verifiedSavingsLabel(25.0, 300.0)
        assertTrue(label.orEmpty().contains("₪25.00"))
        assertTrue(label.orEmpty().contains("₪300.00"))
    }

    @Test
    fun internalBackendTokensAreNeverShownToCustomer() {
        assertEquals("המידע מתעדכן", CustomerPresentationPolicy.safeStatus("GMAIL_READONLY"))
        assertEquals("המידע מתעדכן", CustomerPresentationPolicy.safeStatus("provider_reference=abc"))
        assertEquals("המידע מתעדכן", CustomerPresentationPolicy.safeStatus("Firebase App_Check failed"))
    }

    @Test
    fun pendingStateUsesPlainCustomerLanguage() {
        assertEquals("נמצא בבדיקה", CustomerPresentationPolicy.safeStatus("PENDING_VERIFICATION"))
    }
}
