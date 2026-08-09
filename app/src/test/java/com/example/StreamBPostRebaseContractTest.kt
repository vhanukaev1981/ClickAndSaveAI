package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBPostRebaseContractTest {
    private val dashboard = File("src/main/java/com/example/ui/screens/DashboardScreen.kt")
    private val savings = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt")

    @Test
    fun dashboardKeepsAuthoritativeFinancialHomeContract() {
        val source = dashboard.readText()

        assertTrue(source.contains("BackendRepository()"))
        assertTrue(source.contains("getFinancialHome()"))
        assertTrue(source.contains("session.isAuthenticated"))
        assertTrue(source.contains("isGmailConnected"))
        assertTrue(source.contains("observedRecurringMonthlySpend"))
        assertTrue(source.contains("recurringServiceCount"))
    }

    @Test
    fun savingsKeepsTwoPhaseExactOfferBinding() {
        val source = savings.readText()

        assertTrue(source.contains("recordSavingsActionStarted("))
        assertTrue(source.contains("acceptSavingsOpportunity("))
        assertTrue(source.contains("expectedOfferId = displayedOfferId"))
        assertTrue(source.contains("opportunity.matchedOffer?.offerId.orEmpty()"))
        assertTrue(source.contains("IN_APP_PROVIDER_REQUEST"))
    }

    @Test
    fun savingsKeepsExplicitContactConsentBeforeFinalAcceptance() {
        val source = savings.readText()

        assertTrue(source.contains("savings_contact_name"))
        assertTrue(source.contains("savings_contact_phone"))
        assertTrue(source.contains("savings_contact_email"))
        assertTrue(source.contains("savings_contact_consent"))
        assertTrue(source.contains("submit_savings_request"))
    }

    @Test
    fun postRebaseGuardKeepsCustomerPresentationOnTopOfCoreSemantics() {
        val dashboardSource = dashboard.readText()
        val savingsSource = savings.readText()

        assertTrue(dashboardSource.contains("CustomerPresentationPolicy"))
        assertTrue(dashboardSource.contains("FinancialUiStatePolicy"))
        assertTrue(savingsSource.contains("CustomerPresentationPolicy"))
        assertTrue(savingsSource.contains("savings_retry_refresh"))
    }
}
