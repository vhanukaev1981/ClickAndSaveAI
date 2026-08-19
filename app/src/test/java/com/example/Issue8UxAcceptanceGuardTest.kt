package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Issue8UxAcceptanceGuardTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun activeCustomerUiDoesNotExposeImplementationDiagnostics() {
        val mainActivity = source("src/main/java/com/example/MainActivity.kt")
        val dashboard = source("src/main/java/com/example/ui/screens/DashboardScreen.kt")
        val invoices = source("src/main/java/com/example/ui/screens/InvoicesScreen.kt")
        val providers = source("src/main/java/com/example/ui/screens/ProvidersScreen.kt")
        val activity = source("src/main/java/com/example/ui/screens/ActivityScreen.kt")
        val profile = source("src/main/java/com/example/ui/screens/ProfileScreen.kt")

        assertFalse(mainActivity.contains("Firebase/OAuth"))
        assertFalse(mainActivity.contains("הרשאת gmail.readonly לא אושרה"))
        assertFalse(mainActivity.contains("קוד שרת"))

        assertFalse(dashboard.contains("sourceCoverage.joinToString"))
        assertFalse(dashboard.contains("מהשרת"))

        assertFalse(invoices.contains("${'$'}{bill.verificationStatus}"))
        assertFalse(invoices.contains("${'$'}{opportunity.status}"))

        assertFalse(providers.contains("${'$'}{matched.verificationState}"))
        assertFalse(providers.contains("${'$'}{matched.freshnessState}"))
        assertFalse(providers.contains("${'$'}{matched.eligibilityState}"))
        assertFalse(providers.contains("${'$'}{opportunity.offerVerificationState}"))
        assertFalse(providers.contains("${'$'}{opportunity.offerFreshnessState}"))
        assertFalse(providers.contains("${'$'}{opportunity.userEligibilityState}"))
        assertFalse(providers.contains("throwable.localizedMessage"))

        assertFalse(activity.contains("מצב UI"))
        assertFalse(activity.contains("ledger"))
        assertFalse(activity.contains("add(\"מצב: ${'$'}{event.status}\")"))
        assertFalse(activity.contains("event.verificationStatus?.let { add(\"אימות: ${'$'}it\") }"))
        assertFalse(activity.contains("event.destination"))
        assertFalse(activity.contains("else -> event.type"))
        assertFalse(activity.contains("sourceCoverage.joinToString"))

        assertFalse(profile.contains("authState.message"))
        assertFalse(profile.contains("financialSyncState.reason"))
        listOf(
            "מצב השרת",
            "בשרת",
            "session המקומי",
            "רישום ה-Push",
            "watch",
            "מחזורי חיים",
            "ניקוי שרתי"
        ).forEach { technicalText -> assertFalse(profile.contains(technicalText)) }
    }

    @Test
    fun customerVisibleLeadTerminologyIsAbsentFromCurrentV2() {
        val activeCustomerUi = listOf(
            "src/main/java/com/example/MainActivity.kt",
            "src/main/java/com/example/ui/components/BottomNavBar.kt",
            "src/main/java/com/example/ui/screens/DashboardScreen.kt",
            "src/main/java/com/example/ui/screens/InvoicesScreen.kt",
            "src/main/java/com/example/ui/screens/ProvidersScreen.kt",
            "src/main/java/com/example/ui/screens/ActivityScreen.kt",
            "src/main/java/com/example/ui/screens/ProfileScreen.kt"
        ).joinToString("\n") { source(it) }

        assertFalse(activeCustomerUi.contains("lead", ignoreCase = true))
        assertFalse(activeCustomerUi.contains("ליד"))
    }

    @Test
    fun visibleCtasResolveToImplementedNavigationOrActions() {
        val dashboard = source("src/main/java/com/example/ui/screens/DashboardScreen.kt")
        val invoices = source("src/main/java/com/example/ui/screens/InvoicesScreen.kt")
        val providers = source("src/main/java/com/example/ui/screens/ProvidersScreen.kt")
        val activity = source("src/main/java/com/example/ui/screens/ActivityScreen.kt")
        val profile = source("src/main/java/com/example/ui/screens/ProfileScreen.kt")
        val screens = listOf(dashboard, invoices, providers, activity, profile)

        screens.forEach { screen ->
            assertFalse(Regex("onClick\\s*=\\s*\\{\\s*}").containsMatchIn(screen))
            assertFalse(screen.contains("TODO", ignoreCase = true))
        }

        assertTrue(dashboard.contains("onOpenInvoices"))
        assertTrue(dashboard.contains("onNavigateToTab(1)"))
        assertTrue(dashboard.contains("onNavigateToTab(3)"))
        assertTrue(dashboard.contains("onNavigateToTab(4)"))
        assertTrue(dashboard.contains("FinancialRefreshReason.RETRY"))
        assertTrue(invoices.contains("onClick = { selectedCategory = category }"))
        assertTrue(providers.contains("recordSavingsActionStarted"))
        assertTrue(providers.contains("acceptSavingsOpportunity"))
        assertTrue(activity.contains("FinancialRefreshReason.RETRY"))
        assertTrue(profile.contains("viewModel::disconnectGmail"))
        assertTrue(profile.contains("viewModel::deleteImportedFinancialData"))
        assertTrue(profile.contains("viewModel::deleteAccount"))
    }

    @Test
    fun gmailFirstConnectionUiIsNotRepeatedForConnectedOrUnknownState() {
        val dashboard = source("src/main/java/com/example/ui/screens/DashboardScreen.kt")
        val profile = source("src/main/java/com/example/ui/screens/ProfileScreen.kt")

        assertTrue(dashboard.contains("FinancialSyncState.Unauthenticated -> item"))
        assertTrue(dashboard.contains("FinancialSyncState.Disconnected -> item"))
        assertTrue(Regex("V3OnboardingContent\\(").findAll(dashboard).count() == 2)
        assertFalse(dashboard.contains("InitialGmailOnboardingCard("))
        assertTrue(dashboard.contains("is FinancialSyncState.Ready ->"))
        assertTrue(profile.contains("financialSyncState == FinancialSyncState.Disconnected"))
        assertFalse(profile.contains("connection?.connected != true"))
    }
}
