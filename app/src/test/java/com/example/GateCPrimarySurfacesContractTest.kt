package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateCPrimarySurfacesContractTest {
    @Test
    fun billsConsumesUnifiedRecoveryStateWithoutLocalFinancialFallbacksOrManualProductionEntry() {
        val source = File("src/main/java/com/example/ui/screens/InvoicesScreen.kt").readText()

        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertFalse(source.contains("viewModel.totalMonthlyCost.collectAsState()"))
        assertFalse(source.contains("viewModel.totalMonthlySavingsPotential.collectAsState()"))
        assertFalse(source.contains("ManualInvoiceDialog("))
        assertFalse(source.contains("viewModel.addManualInvoice("))
    }

    @Test
    fun savingsConsumesUnifiedAuthoritativeSessionInsteadOfFetchingFinancialHomeIndependently() {
        val source = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()

        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("viewModel.authoritativeFinancialHome.collectAsState()"))
        assertFalse(source.contains("BackendRepository"))
        assertFalse(source.contains("backendRepository.getFinancialHome()"))
        assertFalse(source.contains("LaunchedEffect("))
    }

    @Test
    fun financialContractKeepsUnknownAuthoritativeValuesNullable() {
        val backend = File("src/main/java/com/example/data/repository/BackendRepository.kt").readText()
        val action = File("src/main/java/com/example/data/repository/OpportunityActionRepository.kt").readText()

        assertTrue(backend.contains("val observedRecurringMonthlySpend: Double?"))
        assertTrue(backend.contains("val recurringServiceCount: Int?"))
        assertFalse(backend.contains("observedRecurringMonthlySpend = (contextMap[\"observedRecurringMonthlySpend\"] as? Number)?.toDouble() ?: 0.0"))
        assertFalse(backend.contains("recurringServiceCount = (contextMap[\"recurringServiceCount\"] as? Number)?.toInt() ?: 0"))
        assertTrue(action.contains("val potentialMonthlySaving: Double?"))
        assertTrue(action.contains("val potentialAnnualSaving: Double?"))
        assertFalse(action.contains("potentialMonthlySaving = (response[\"potentialMonthlySaving\"] as? Number)?.toDouble() ?: 0.0"))
        assertFalse(action.contains("potentialAnnualSaving = (response[\"potentialAnnualSaving\"] as? Number)?.toDouble() ?: 0.0"))
    }

    @Test
    fun savingsNeverClaimsProviderDeliveryOrAnnualSavingsWithoutEvidence() {
        val source = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()

        assertFalse(source.contains("potentialAnnualSaving ?: 0.0"))
        assertFalse(source.contains("\"USER_ACCEPTED\" -> \"הבקשה נשלחה""))
        assertTrue(source.contains("שלחו את הפרטים לספק"))
        assertTrue(source.contains("טרם אושר שהספק קיבל"))
    }
}
