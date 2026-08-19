package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Block4SavingsHandoffTruthGuardTest {
    @Test
    fun acceptedOpportunityUsesProofOrientedProviderHandoffFields() {
        val repository = File("src/main/java/com/example/data/repository/OpportunityActionRepository.kt").readText()
        listOf(
            "offerVerificationState",
            "offerFreshnessState",
            "userEligibilityState",
            "deliveryState",
            "providerContactState",
            "completionState",
            "dealObserved",
            "savingRealizationState"
        ).forEach { contract ->
            assertTrue("missing proof field: $contract", repository.contains(contract))
        }
        assertTrue(repository.contains("require(request.htmlCode in 200..299)"))
        assertTrue(repository.contains("payload[\"offerId\"]"))
        assertTrue(repository.contains("payload[\"contactName\"]"))
        assertTrue(repository.contains("payload[\"contactEmail\"]"))
        assertTrue(repository.contains("payload[\"phone\"]"))
        assertTrue(repository.contains("payload[\"consentAccepted\"] = true"))
    }

    @Test
    fun explicitConsentIsRequiredAndNoDefaultConsentIsInvented() {
        val ui = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        assertTrue(ui.contains("mutableStateOf(false)"))
        assertTrue(ui.contains("consentAccepted = consent"))
        assertFalse(Regex("consentAccepted\\s*=\\s*true").containsMatchIn(ui))
    }

    @Test
    fun providerUiFailsClosedThroughTheV3PresentationTruthBoundary() {
        val ui = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        val presentation = File("src/main/java/com/example/ui/v3/V3FinancialPresentation.kt").readText()
        assertTrue(ui.contains("hasVerifiedSavingsActionTarget"))
        listOf(
            "offerVerificationState == \"VERIFIED\"",
            "offerFreshnessState == \"FRESH\"",
            "userEligibilityState == \"ELIGIBLE\"",
            "offer.verificationState == \"VERIFIED\"",
            "offer.freshnessState == \"FRESH\"",
            "offer.eligibilityState == \"ELIGIBLE\"",
            "requestState",
            "submissionState",
            "deliveryState",
            "providerContactState",
            "completionState",
            "savingRealizationState"
        ).forEach { truthBoundary -> assertTrue("missing V3 truth boundary: $truthBoundary", presentation.contains(truthBoundary)) }
        assertFalse(ui.contains("CRM was notified", ignoreCase = true))
        assertFalse(ui.contains("הספק קיבל"))
        assertFalse(ui.contains("העסקה הושלמה"))
    }
}
