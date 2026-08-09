package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerUiSourceGuardTest {
    private val screenPaths = listOf(
        "src/main/java/com/example/ui/screens/DashboardScreen.kt",
        "src/main/java/com/example/ui/screens/InvoicesScreen.kt",
        "src/main/java/com/example/ui/screens/ProvidersScreen.kt",
        "src/main/java/com/example/ui/screens/ProfileScreen.kt",
        "src/main/java/com/example/ui/screens/SettingsScreen.kt"
    )

    @Test
    fun customerScreensDoNotRenderRawLocalizedBackendMessages() {
        screenPaths.forEach { path ->
            val text = File(path).readText()
            assertFalse("$path must not expose localizedMessage", text.contains("localizedMessage"))
            assertFalse("$path must not expose stack traces", text.contains("stackTraceToString"))
        }
    }

    @Test
    fun customerScreensDoNotContainLegacyTechnicalProductCopy() {
        val forbiddenVisiblePhrases = listOf(
            "Firebase Auth",
            "App Check",
            "Secret Manager",
            "שמור ליד",
            "חשבוניות ולידים",
            "ליד חם",
            "קטלוג להדגמה"
        )
        screenPaths.forEach { path ->
            val text = File(path).readText()
            forbiddenVisiblePhrases.forEach { phrase ->
                assertFalse("$path contains forbidden customer copy: $phrase", text.contains(phrase))
            }
        }
    }

    @Test
    fun coreFinancialDestinationsKeepStableTestHooks() {
        val dashboard = File(screenPaths[0]).readText()
        val bills = File(screenPaths[1]).readText()
        val savings = File(screenPaths[2]).readText()
        val profile = File(screenPaths[3]).readText()
        val settings = File(screenPaths[4]).readText()

        assertTrue(dashboard.contains("dashboard_screen"))
        assertTrue(bills.contains("invoices_screen"))
        assertTrue(savings.contains("providers_screen"))
        assertTrue(profile.contains("profile_screen"))
        assertTrue(settings.contains("settings_screen"))
    }

    @Test
    fun primaryCustomerActionsKeepStableE2eHooks() {
        val dashboard = File(screenPaths[0]).readText()
        val bills = File(screenPaths[1]).readText()
        val savings = File(screenPaths[2]).readText()
        val profile = File(screenPaths[3]).readText()
        val settings = File(screenPaths[4]).readText()

        listOf(
            "dashboard_savings_hero",
            "dashboard_connect_account",
            "dashboard_manage_bills",
            "dashboard_manage_savings",
            "dashboard_manage_profile"
        ).forEach { tag -> assertTrue("Dashboard lost E2E hook $tag", dashboard.contains(tag)) }

        listOf(
            "bills_monthly_overview",
            "add_manual_bill",
            "save_manual_bill",
            "confirm_delete_bill",
            "cancel_delete_bill"
        ).forEach { tag -> assertTrue("Bills lost E2E hook $tag", bills.contains(tag)) }

        listOf(
            "savings_loading_state",
            "savings_under_review_state",
            "savings_error_state",
            "accept_savings_",
            "savings_contact_consent",
            "submit_savings_request",
            "cancel_savings_request"
        ).forEach { tag -> assertTrue("Savings lost E2E hook $tag", savings.contains(tag)) }

        listOf(
            "open_savings_preferences",
            "open_privacy_connections",
            "privacy_connections_screen",
            "disconnect_document_source"
        ).forEach { tag -> assertTrue("Profile lost E2E hook $tag", profile.contains(tag)) }

        assertTrue("Settings lost save hook", settings.contains("save_savings_preferences"))
    }

    @Test
    fun bottomNavigationKeepsRtlAndAccessibleTouchTargetContract() {
        val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()
        assertTrue(nav.contains("LayoutDirection.Rtl"))
        assertTrue(nav.contains("FinancialDesignTokens.minimumTouchTarget"))
        assertTrue(nav.contains("contentDescription"))
        listOf("nav_dashboard", "nav_invoices", "nav_savings", "nav_profile").forEach { tag ->
            assertTrue("Navigation lost stable destination $tag", nav.contains(tag))
        }
    }
}
