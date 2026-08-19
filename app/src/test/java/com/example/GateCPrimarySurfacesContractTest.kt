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
        val homeComponents = File("src/main/java/com/example/ui/components/V3HomeComponents.kt").readText()
        assertTrue(source.contains("dashboard_screen"))
        assertTrue(source.contains("v3_savings_hero"))
        assertTrue(source.contains("v3_next_best_action"))
        assertTrue(source.contains("v3_monitoring_status"))
        assertTrue(source.contains("v3_open_invoices"))
        assertTrue(source.contains("toV3SavingsSummary"))
        assertTrue(source.contains("NextBestActionCard("))
        assertTrue(homeComponents.contains("הדבר הכי משתלם לעשות עכשיו"))
    }

    @Test
    fun payConsumesAuthoritativeRecoveredBillsWithoutManualOrRoomFallbacks() {
        val source = File("src/main/java/com/example/ui/screens/InvoicesScreen.kt").readText()
        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("latestScanOrNull"))
        assertTrue(source.contains("מועד לתשלום: לא ידוע"))
        assertTrue(source.contains("NO_VERIFIED_PAYMENT_TARGET"))
        assertFalse(source.contains("viewModel.invoices.collectAsState()"))
        assertFalse(source.contains("ManualInvoiceDialog("))
        assertFalse(source.contains("paymentUrl"))
    }

    @Test
    fun savingsConsumesUnifiedAuthoritativeSessionAndKeepsPotentialSeparate() {
        val source = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("viewModel.authoritativeFinancialHome.collectAsState()"))
        assertFalse(source.contains("BackendRepository"))
        assertFalse(source.contains("LaunchedEffect("))
        assertTrue(source.contains("חיסכון פוטנציאלי"))
        assertTrue(source.contains("לא חיסכון ממומש") || source.contains("אינו חיסכון ממומש"))
        assertFalse(source.contains("חיסכון מאומת"))
    }

    @Test
    fun savingsUsesFrozenActionBoundaryAndExistingLeadFlow() {
        val source = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        assertTrue(source.contains("v3LifecycleLabel"))
        assertTrue(source.contains("hasAuthoritativeV3Offer"))
        assertTrue(source.contains("V3SavingsActionMode.PROVIDER_LEAD_FLOW"))
        assertTrue(source.contains("recordSavingsActionStarted"))
        assertTrue(source.contains("acceptSavingsOpportunity"))
        assertTrue(source.contains("לא הוכחה לשינוי תעריף"))
        assertFalse(source.contains("potentialMonthlySaving ?: 0.0"))
    }

    @Test
    fun profileUsesFourFrozenGroupsAndAuthoritativeGmailState() {
        val source = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()
        assertTrue(source.contains("viewModel.financialSyncState.collectAsState()"))
        assertTrue(source.contains("viewModel.authState.collectAsState()"))
        listOf("חיבור ונתונים", "פרטיות והרשאות", "פעילות והתראות", "חשבון ואבטחה").forEach {
            assertTrue(source.contains("V3SectionHeader(\"$it\")"))
        }
        assertTrue(source.contains("disconnectGmail"))
        assertTrue(source.contains("deleteImportedFinancialData"))
        assertTrue(source.contains("deleteAccount"))
        assertTrue(source.contains("קריאה בלבד"))
    }

    @Test
    fun activityIsSecondaryAndPayIsPrimary() {
        val activity = File("src/main/java/com/example/ui/screens/ActivityScreen.kt").readText()
        val invoices = File("src/main/java/com/example/ui/screens/InvoicesScreen.kt").readText()
        val shell = File("src/main/java/com/example/MainActivity.kt").readText()
        assertTrue(activity.contains("ActivityTimelineItem("))
        assertTrue(activity.contains("activityGroupLabel("))
        assertFalse(activity.contains("CRM", ignoreCase = true))
        assertTrue(invoices.contains("v3_invoice_list"))
        assertTrue(invoices.contains("v3_invoice_item"))
        assertTrue(invoices.contains("receivedDate"))
        assertTrue(invoices.contains("verificationLabel"))
        assertTrue(shell.contains("V3SecondarySurface.ACTIVITY"))
        assertTrue(shell.contains("3 -> InvoicesScreen("))
        assertTrue(shell.contains("contentDescription = \"חזרה\""))
    }

    @Test
    fun financialContractKeepsUnknownAuthoritativeValuesNullable() {
        val backend = File("src/main/java/com/example/data/repository/BackendRepository.kt").readText()
        val action = File("src/main/java/com/example/data/repository/OpportunityActionRepository.kt").readText()
        assertTrue(backend.contains("val observedRecurringMonthlySpend: Double?"))
        assertTrue(backend.contains("val recurringServiceCount: Int?"))
        assertTrue(action.contains("val potentialMonthlySaving: Double?"))
        assertTrue(action.contains("val potentialAnnualSaving: Double?"))
        assertFalse(action.contains("potentialMonthlySaving = (response[\"potentialMonthlySaving\"] as? Number)?.toDouble() ?: 0.0"))
        assertFalse(action.contains("potentialAnnualSaving = (response[\"potentialAnnualSaving\"] as? Number)?.toDouble() ?: 0.0"))
    }
}
