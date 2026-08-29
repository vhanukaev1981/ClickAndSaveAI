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
            "potentialMonthlySaving",
            "potentialAnnualSaving",
            "consentState",
            "requestState",
            "deliveryAttemptState",
            "submissionState",
            "deliveryState",
            "providerContactState",
            "completionState",
            "savingRealizationState",
            "realizedMonthlySaving",
            "realizedAnnualSaving"
        ).forEach { contract ->
            assertTrue("missing action result field: $contract", repository.contains(contract))
        }
        assertTrue(repository.contains("require(consentAccepted)"))
        assertTrue(repository.contains("\"expectedOfferId\" to expectedOfferId"))
        assertTrue(repository.contains("\"contactName\" to contactName"))
        assertTrue(repository.contains("\"contactEmail\" to contactEmail"))
        assertTrue(repository.contains("\"phone\" to phone"))
        assertTrue(repository.contains("\"consentAccepted\" to consentAccepted"))
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
