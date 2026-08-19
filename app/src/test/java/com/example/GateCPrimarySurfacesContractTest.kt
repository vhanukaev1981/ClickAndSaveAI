package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateCPrimarySurfacesContractTest {
    @Test
    fun homeUsesAuthoritativeScanAndNeverLeaksLocalInvoiceCache() {
        val source = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue(source.contains("financialSyncState"))
        assertTrue(source.contains("latestScanOrNull") || source.contains("latestRecoveredGmailScan"))
        assertFalse(source.contains("viewModel.invoices.collectAsState()"))
        assertFalse(source.contains("viewModel.isGmailConnected.collectAsState()"))
    }

    @Test
    fun homeUsesApprovedV3CommandCenterHierarchy() {
        val source = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue(source.contains("dashboard_screen"))
        assertTrue(source.contains("v3_savings_hero"))
        assertTrue(source.contains("v3_next_best_action"))
        assertTrue(source.contains("v3_monitoring_status"))
        assertTrue(source.contains("v3_open_invoices"))
        assertTrue(source.contains("toV3SavingsSummary"))
        assertTrue(source.contains("הדבר הכי משתלם לעשות עכשיו"))
        assertFalse(source.contains("viewModel.invoices.collectAsState()"))
    }

    @Test
    fun billsConsumesAuthoritativeRecoveredBillsWithoutManualOrRoomFallbacks() {
        val source = File("src/main/java/com/example/ui/screens/InvoicesScreen.kt").readText()
        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("latestScanOrNull") || source.contains("latestRecoveredGmailScan"))
        assertFalse(source.contains("viewModel.invoices.collectAsState()"))
        assertFalse(source.contains("InvoiceItem"))
        assertFalse(source.contains("viewModel.deleteInvoice("))
        assertFalse(source.contains("ManualInvoiceDialog("))
        assertFalse(source.contains("viewModel.addManualInvoice("))
        assertFalse(source.contains("viewModel.totalMonthlyCost.collectAsState()"))
        assertFalse(source.contains("viewModel.totalMonthlySavingsPotential.collectAsState()"))
    }

    @Test
    fun savingsConsumesUnifiedAuthoritativeSessionAndCallsSavingsPotentialNotConfirmed() {
        val source = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("viewModel.authoritativeFinancialHome.collectAsState()"))
        assertFalse(source.contains("BackendRepository"))
        assertFalse(source.contains("backendRepository.getFinancialHome()"))
        assertFalse(source.contains("LaunchedEffect("))
        assertTrue(source.contains("חיסכון פוטנציאלי"))
        assertFalse(source.contains("חיסכון מאומת"))
        assertTrue(source.contains("סטטוס אימות ההצעה לא ידוע"))
    }

    @Test
    fun savingsUnknownMonetaryValueCannotCreateAMonetarySavingsCta() {
        val source = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        assertFalse(source.contains("potentialAnnualSaving ?: 0.0"))
        assertFalse(source.contains("potentialMonthlySaving ?: 0.0"))
        assertTrue(source.contains("monthlySaving != null"))
    }

    @Test
    fun meUsesAuthoritativeSyncContractForGmailAndDoesNotPresentLocalDefaultsAsServerTruth() {
        val source = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()
        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("viewModel.authState.collectAsState()"))
        assertFalse(source.contains("viewModel.isGmailConnected.collectAsState()"))
        assertFalse(source.contains("viewModel.monthlySavingsGoal.collectAsState()"))
        assertFalse(source.contains("viewModel.minSavingsThreshold.collectAsState()"))
        assertTrue(source.contains("מצב Gmail לא ידוע"))
        assertTrue(source.contains("קריאה בלבד"))
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
}
