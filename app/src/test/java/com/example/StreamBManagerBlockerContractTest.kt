package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBManagerBlockerContractTest {
    private val dashboard = File("src/main/java/com/example/ui/screens/DashboardScreen.kt")
    private val savings = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt")

    @Test
    fun recurringContextNeverFallsBackToLocalInvoiceCountOrUnqualifiedSpend() {
        val source = dashboard.readText()

        assertTrue(source.contains("financialHome?.context?.recurringServiceCount"))
        assertTrue(source.contains("recurringServiceCount?.toString() ?: \"—\""))
        assertFalse(source.contains("recurringServiceCount ?: invoices.size"))
        assertFalse(source.contains("observedRecurringMonthlySpend?.takeIf { it > 0.0 } ?: localTotalMonthlyCost"))
        assertTrue(source.contains("לא בהכרח חיוב חוזר"))
    }

    @Test
    fun androidNeverSynthesizesAnnualSavingsEconomics() {
        val dashboardSource = dashboard.readText()
        val savingsSource = savings.readText()

        assertFalse(dashboardSource.contains("potentialMonthlySaving * 12.0"))
        assertFalse(dashboardSource.contains("monthlySavings * 12.0"))
        assertFalse(savingsSource.contains("result.potentialMonthlySaving * 12.0"))
        assertTrue(savingsSource.contains("result.potentialAnnualSaving"))
    }

    @Test
    fun actionStartedIsRecordedBeforeConsentAndExactOfferRemainsPinned() {
        val source = savings.readText()

        assertTrue(source.contains("OpportunityActionRepository()"))
        assertTrue(source.contains("recordSavingsActionStarted("))
        assertTrue(source.contains("expectedOfferId = displayedOfferId"))
        assertTrue(source.contains("selectedOpportunity = opportunity"))
        assertTrue(source.indexOf("recordSavingsActionStarted(") < source.indexOf("selectedOpportunity = opportunity"))
        assertTrue(source.contains("if (displayedOfferId.isBlank())"))
        assertTrue(source.contains("acceptSavingsOpportunity("))
    }

    @Test
    fun explicitConsentRemainsBetweenIntentAndFinalProviderRequest() {
        val source = savings.readText()

        assertTrue(source.contains("SavingsActionDialog("))
        assertTrue(source.contains("savings_contact_consent"))
        assertTrue(source.contains("submit_savings_request"))
        assertTrue(source.contains("תוכן תיבת הדואר ותמונת ההוצאות המלאה שלך אינם נשלחים"))
    }
}
