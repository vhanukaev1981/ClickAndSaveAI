package com.example

import java.io.File
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
    fun homeUsesTheCorrectFiveDestinationNavigationIndices() {
        val source = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()

        assertTrue(source.contains("onNavigateToTab(4)"))
        assertFalse(source.contains("onNavigateToTab(3)\n                )\n            }\n        }\n\n        item {\n            Text(\n                \"פעילות אחרונה\""))
    }
}
