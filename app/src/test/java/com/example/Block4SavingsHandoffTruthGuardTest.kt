package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Block4SavingsHandoffTruthGuardTest {
    @Test
    fun savingsProjectionKeepsPotentialRealizedAndUnknownIndependent() {
        val repository = File("src/main/java/com/example/data/repository/SavingsOpportunityRepository.kt").readText()

        assertTrue(repository.contains("val potentialMonthlySaving: Double?"))
        assertTrue(repository.contains("val realizedMonthlySaving: Double?"))
        assertTrue(repository.contains("val offerVerificationState: String"))
        assertTrue(repository.contains("val offerFreshnessState: String"))
        assertTrue(repository.contains("val userEligibilityState: String"))
        assertTrue(repository.contains("val submissionState: String"))
        assertTrue(repository.contains("val deliveryState: String"))
        assertTrue(repository.contains("val providerContactState: String"))
        assertTrue(repository.contains("val completionState: String"))
        assertTrue(repository.contains("val savingRealizationState: String"))
        assertTrue(repository.contains("previousMonthlyCost = (map[\"previousMonthlyCost\"] as? Number)?.toDouble()"))
        assertFalse(repository.contains("previousMonthlyCost = (map[\"previousMonthlyCost\"] as? Number)?.toDouble() ?: 0.0"))
    }

    @Test
    fun explicitConsentIsRequiredAndNoDefaultConsentIsInvented() {
        val repository = File("src/main/java/com/example/data/repository/OpportunityActionRepository.kt").readText()
        val screen = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()

        assertTrue(repository.contains("consentAccepted: Boolean\n"))
        assertFalse(repository.contains("consentAccepted: Boolean = true"))
        assertTrue(repository.contains("require(consentAccepted)"))
        assertTrue(screen.contains("Checkbox(checked = accepted"))
        assertTrue(screen.contains("consentAccepted = true"))
    }

    @Test
    fun providerUiFailsClosedOnOfferTruthAndUsesIndependentHandoffStates() {
        val screen = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()

        assertTrue(screen.contains("offerVerificationState == \"VERIFIED\""))
        assertTrue(screen.contains("offerFreshnessState == \"FRESH\""))
        assertTrue(screen.contains("userEligibilityState == \"ELIGIBLE\""))
        assertTrue(screen.contains("offer.verificationState == \"VERIFIED\""))
        assertTrue(screen.contains("offer.freshnessState == \"FRESH\""))
        assertTrue(screen.contains("offer.eligibilityState == \"ELIGIBLE\""))
        assertTrue(screen.contains("deliveryState == \"DELIVERY_CONFIRMED\""))
        assertTrue(screen.contains("providerContactState == \"CONTACTED\""))
        assertTrue(screen.contains("completionState == \"DEAL_COMPLETED\""))
        assertTrue(screen.contains("savingRealizationState == \"REALIZED\""))
        assertTrue(screen.contains("זה אינו חיסכון ממומש"))
    }

    @Test
    fun dashboardDelegatesVerifiedFreshEligibleTruthToV3PresentationMapper() {
        val dashboard = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        val mapper = File("src/main/java/com/example/ui/v3/V3FinancialPresentation.kt").readText()
        val homeComponents = File("src/main/java/com/example/ui/components/V3HomeComponents.kt").readText()

        assertTrue(dashboard.contains("toV3SavingsSummary"))
        assertTrue(mapper.contains("offerVerificationState == \"VERIFIED\""))
        assertTrue(mapper.contains("offerFreshnessState == \"FRESH\""))
        assertTrue(mapper.contains("userEligibilityState == \"ELIGIBLE\""))
        assertTrue(mapper.contains("offer.verificationState == \"VERIFIED\""))
        assertTrue(mapper.contains("offer.freshnessState == \"FRESH\""))
        assertTrue(mapper.contains("offer.eligibilityState == \"ELIGIBLE\""))
        assertTrue(homeComponents.contains("חיסכון פוטנציאלי"))
        assertTrue(homeComponents.contains("אינו חיסכון שכבר מומש"))
    }
}
