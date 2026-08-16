package com.example

import com.example.ui.screens.formatAuthoritativeCount
import com.example.ui.screens.formatAuthoritativeMoney
import com.example.ui.screens.formatVerifiedSavings
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynchronizedHomeContractTest {
    @Test
    fun homeConsumesTheUnifiedFinancialSessionInsteadOfFetchingOrFallingBackLocally() {
        val source = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()

        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("viewModel.authoritativeFinancialHome.collectAsState()"))
        assertFalse(source.contains("BackendRepository"))
        assertFalse(source.contains("backendRepository.getFinancialHome()"))
        assertFalse(source.contains("localTotalMonthlyCost"))
        assertFalse(source.contains("financialHome?.context?.observedRecurringMonthlySpend\n        ?.takeIf { it > 0.0 }\n        ?:"))
    }

    @Test
    fun unknownAndRecoveringHomeNeverTurnIntoKnownZeroSavingsOrSpend() {
        val source = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()

        assertTrue(source.contains("FinancialSyncState.CheckingConnection"))
        assertTrue(source.contains("FinancialSyncState.Recovering"))
        assertTrue(source.contains("is FinancialSyncState.Partial"))
        assertTrue(source.contains("is FinancialSyncState.Ready"))
        assertTrue(source.contains("המידע הפיננסי עדיין נטען"))
        assertTrue(source.contains("המידע האחרון נשמר"))
        assertFalse(source.contains("verifiedOpportunities.sumOf"))
        assertFalse(source.contains("verifiedMonthlySavings ="))
        assertFalse(source.contains("verifiedAnnualSavings ="))
    }

    @Test
    fun unknownRecurringSpendDoesNotBecomeZero() {
        assertEquals("לא ידוע", formatAuthoritativeMoney(null))
        assertFalse(formatAuthoritativeMoney(null).contains("₪0"))
    }

    @Test
    fun unknownServiceCountDoesNotBecomeZero() {
        assertEquals("לא ידוע", formatAuthoritativeCount(null))
        assertFalse(formatAuthoritativeCount(null) == "0")
    }

    @Test
    fun unknownSavingsDoesNotBecomeZero() {
        assertEquals("דרוש אימות", formatVerifiedSavings(null))
        assertFalse(formatVerifiedSavings(null).contains("₪0"))
    }

    @Test
    fun knownAuthoritativeZeroCanStillDisplayZero() {
        assertEquals("₪0", formatAuthoritativeMoney(0.0))
        assertEquals("0", formatAuthoritativeCount(0))
        assertEquals("₪0", formatVerifiedSavings(0.0))
    }

    @Test
    fun parserPreservesUnknownAuthoritativeHomeValues() {
        val source = File("src/main/java/com/example/data/repository/BackendRepository.kt").readText()

        assertTrue(source.contains("val observedRecurringMonthlySpend: Double?"))
        assertTrue(source.contains("val recurringServiceCount: Int?"))
        assertFalse(source.contains("observedRecurringMonthlySpend = (contextMap[\"observedRecurringMonthlySpend\"] as? Number)?.toDouble() ?: 0.0"))
        assertFalse(source.contains("recurringServiceCount = (contextMap[\"recurringServiceCount\"] as? Number)?.toInt() ?: 0"))
    }

    @Test
    fun partialRecoveryAndErrorStatesDoNotFabricateFinancialValues() {
        val dashboard = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        val providers = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()

        assertTrue(dashboard.contains("FinancialSyncState.Recovering"))
        assertTrue(dashboard.contains("is FinancialSyncState.Partial"))
        assertTrue(dashboard.contains("is FinancialSyncState.Failed"))
        assertFalse(providers.contains("monthlySaving ?: 0.0"))
        assertFalse(providers.contains("potentialAnnualSaving ?: 0.0"))
        assertFalse(providers.contains("result.potentialMonthlySaving ?: 0.0"))
    }

    @Test
    fun homeUsesTheCorrectFiveDestinationNavigationIndices() {
        val source = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()

        assertTrue(source.contains("onNavigateToTab(4)"))
        assertFalse(source.contains("onNavigateToTab(3)\n                )\n            }\n        }\n\n        item {\n            Text(\n                \"פעילות אחרונה\""))
    }
}
