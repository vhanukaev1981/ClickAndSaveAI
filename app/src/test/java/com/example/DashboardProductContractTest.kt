package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardProductContractTest {
    private val dashboardPath = "src/main/java/com/example/ui/screens/DashboardScreen.kt"

    @Test
    fun documentSourceConnectionRemainsInitialOnboardingOnly() {
        val dashboard = File(dashboardPath).readText()

        assertTrue(dashboard.contains("if (!session.isAuthenticated || !isConnected)"))
        assertTrue(dashboard.contains("InitialGmailOnboardingCard("))
        assertTrue(dashboard.contains("dashboard_initial_connection"))
        assertTrue(dashboard.contains("dashboard_connect_account"))
        assertTrue(dashboard.contains("בחיבור הראשון נבדוק עד 6 חודשים אחורה"))
    }

    @Test
    fun savingsHeroRequiresVerifiedOfferAndNeverFabricatesAnnualSavings() {
        val dashboard = File(dashboardPath).readText()

        assertTrue(dashboard.contains("it.matchedOffer != null"))
        assertTrue(dashboard.contains("potentialAnnualSaving?.takeIf"))
        assertTrue(dashboard.contains("verifiedAnnualValues.size == verifiedOpportunities.size"))
        assertFalse(dashboard.contains("monthlySavings * 12.0"))
        assertFalse(dashboard.contains("potentialMonthlySaving ?: 0.0) * 12.0"))
        assertTrue(dashboard.contains("נציג כאן סכום רק אחרי שנמצא ונאמת חיסכון אמיתי"))
    }

    @Test
    fun dashboardKeepsCustomerRecoveryAndPrimaryNavigationHooks() {
        val dashboard = File(dashboardPath).readText()

        listOf(
            "dashboard_screen",
            "dashboard_savings_hero",
            "dashboard_error_state",
            "dashboard_retry_financial_home",
            "dashboard_manage_bills",
            "dashboard_manage_savings",
            "dashboard_manage_profile",
            "dashboard_all_bills"
        ).forEach { tag ->
            assertTrue("Dashboard lost product/E2E hook $tag", dashboard.contains(tag))
        }

        assertTrue(dashboard.contains("financialHomeRefreshKey += 1"))
        assertTrue(dashboard.contains("financialHomeTemporarilyUnavailable = false"))
    }

    @Test
    fun recurringFinancialContextIsNotInventedFromLocalInvoiceCount() {
        val dashboard = File(dashboardPath).readText()

        assertTrue(dashboard.contains("financialHome?.context?.recurringServiceCount"))
        assertTrue(dashboard.contains("recurringServiceCount?.toString() ?: \"—\""))
        assertFalse(dashboard.contains("recurringServiceCount ?: invoices.size"))
        assertTrue(dashboard.contains("לא בהכרח חיוב חוזר"))
    }
}
