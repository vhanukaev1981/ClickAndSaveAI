package com.example

import com.example.ui.PaymentHandoffStage
import com.example.ui.PaymentHandoffPresentationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentHandoffPresentationPolicyTest {
    @Test
    fun unavailableDestinationNeverProducesPaymentCallToAction() {
        val message = PaymentHandoffPresentationPolicy.message(PaymentHandoffStage.UNAVAILABLE)
        assertFalse(message.actionLabel.orEmpty().contains("שלם"))
        assertTrue(message.body.contains("אין כרגע"))
    }

    @Test
    fun verifiedDestinationMakesProviderBoundaryExplicit() {
        val message = PaymentHandoffPresentationPolicy.message(PaymentHandoffStage.VERIFIED_PROVIDER_DESTINATION)
        val text = "${message.title} ${message.body} ${message.actionLabel}"
        assertTrue(text.contains("אצל הספק"))
        assertTrue(text.contains("כרטיס"))
        assertFalse(text.contains("נחייב"))
    }

    @Test
    fun openedDestinationNeverMarksBillPaid() {
        val message = PaymentHandoffPresentationPolicy.message(PaymentHandoffStage.PROVIDER_PAYMENT_PAGE_OPENED)
        val text = "${message.title} ${message.body}"
        assertTrue(text.contains("נפתח"))
        assertFalse(text.contains("החשבון שולם"))
        assertFalse(text.contains("התשלום הושלם"))
    }

    @Test
    fun paidStateRequiresVerifiedEvidenceStage() {
        val message = PaymentHandoffPresentationPolicy.message(PaymentHandoffStage.PAYMENT_VERIFIED)
        assertTrue("${message.title} ${message.body}".contains("אומת"))
    }
}
