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
    fun customerScreensContainNoDeadOrComingSoonActions() {
        val emptyClick = Regex("onClick\\s*=\\s*\\{\\s*}")
        val forbiddenImplementationMarkers = listOf(
            "TODO(",
            "NotImplementedError",
            "Coming soon",
            "coming soon",
            "בקרוב"
        )

        screenPaths.forEach { path ->
            val text = File(path).readText()
            assertFalse("$path contains an empty onClick handler", emptyClick.containsMatchIn(text))
            forbiddenImplementationMarkers.forEach { marker ->
                assertFalse("$path contains unfinished customer action marker: $marker", text.contains(marker))
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
            "dashboard_retry_financial_home",
            "dashboard_manage_bills",
            "dashboard_manage_savings",
            "dashboard_manage_profile"
        ).forEach { tag -> assertTrue("Dashboard lost E2E hook $tag", dashboard.contains(tag)) }

        listOf(
            "bills_monthly_overview",
            "add_manual_bill",
            "save_manual_bill",
            "confirm_delete_bill",
            "cancel_delete_bill",
            "bills_action_feedback"
        ).forEach { tag -> assertTrue("Bills lost E2E hook $tag", bills.contains(tag)) }

        listOf(
            "savings_loading_state",
            "savings_under_review_state",
            "savings_error_state",
            "savings_action_submitting",
            "accept_savings_",
            "savings_contact_consent",
            "submit_savings_request",
            "cancel_savings_request"
        ).forEach { tag -> assertTrue("Savings lost E2E hook $tag", savings.contains(tag)) }

        listOf(
            "profile_sign_out",
            "confirm_profile_sign_out",
            "cancel_profile_sign_out",
            "open_savings_preferences",
            "open_privacy_connections",
            "privacy_connections_screen",
            "disconnect_document_source",
            "confirm_disconnect_document_source",
            "cancel_disconnect_document_source"
        ).forEach { tag -> assertTrue("Profile lost E2E hook $tag", profile.contains(tag)) }

        listOf(
            "settings_back",
            "monthly_savings_goal",
            "minimum_savings_threshold",
            "preference_electricity",
            "preference_cellular",
            "preference_internet",
            "preference_insurance",
            "preference_streaming",
            "save_savings_preferences",
            "preferences_saved_confirmation",
            "discard_preferences_changes",
            "keep_editing_preferences"
        ).forEach { tag -> assertTrue("Settings lost E2E hook $tag", settings.contains(tag)) }
    }

    @Test
    fun dashboardErrorStateKeepsExplicitRetryPath() {
        val dashboard = File(screenPaths[0]).readText()
        assertTrue(dashboard.contains("financialHomeRefreshKey"))
        assertTrue(dashboard.contains("dashboard_error_state"))
        assertTrue(dashboard.contains("dashboard_retry_financial_home"))
        assertTrue(dashboard.contains("financialHomeTemporarilyUnavailable = false"))
        assertTrue(dashboard.contains("financialHomeRefreshKey += 1"))
    }

    @Test
    fun destructiveProfileActionsRemainExplicitlyConfirmed() {
        val profile = File(screenPaths[3]).readText()

        assertTrue(profile.contains("showSignOutConfirmation"))
        assertTrue(profile.contains("confirm_profile_sign_out"))
        assertTrue(profile.contains("cancel_profile_sign_out"))
        assertFalse(
            "Sign-out CTA must not invoke signOut directly",
            profile.contains("onClick = viewModel::signOut")
        )

        assertTrue(profile.contains("showDisconnectConfirmation"))
        assertTrue(profile.contains("confirm_disconnect_document_source"))
        assertTrue(profile.contains("cancel_disconnect_document_source"))
        assertFalse(
            "Disconnect CTA must not call disconnect directly",
            profile.contains("onClick = viewModel::disconnectGmail")
        )
    }

    @Test
    fun billsMutationsKeepVisibleInAppFeedback() {
        val bills = File(screenPaths[1]).readText()
        assertTrue(bills.contains("actionFeedback"))
        assertTrue(bills.contains("bills_action_feedback"))
        assertTrue(bills.contains("החשבון של"))
    }

    @Test
    fun savingsSubmissionExposesProgressAndBlocksDuplicateActions() {
        val savings = File(screenPaths[2]).readText()
        assertTrue(savings.contains("actionSubmitting"))
        assertTrue(savings.contains("savings_action_submitting"))
        assertTrue(savings.contains("actionEnabled = !actionSubmitting"))
        assertTrue(savings.contains("enabled = actionEnabled"))
        assertTrue(savings.contains("if (actionSubmitting) return@SavingsActionDialog"))
    }

    @Test
    fun preferenceSaveKeepsVisibleInAppFeedbackAndProtectsUnsavedChanges() {
        val settings = File(screenPaths[4]).readText()
        assertTrue(settings.contains("savedSignature"))
        assertTrue(settings.contains("preferences_saved_confirmation"))
        assertTrue(settings.contains("persistedSignature"))
        assertTrue(settings.contains("hasUnsavedChanges"))
        assertTrue(settings.contains("showDiscardConfirmation"))
        assertTrue(settings.contains("discard_preferences_changes"))
        assertTrue(settings.contains("keep_editing_preferences"))
        assertFalse("Settings must not rely on system Toast feedback", settings.contains("Toast.makeText"))
    }

    @Test
    fun profileAndSettingsUseSharedFinancialDesignTokens() {
        val profile = File(screenPaths[3]).readText()
        val settings = File(screenPaths[4]).readText()
        listOf(profile, settings).forEach { text ->
            assertTrue(text.contains("FinancialDesignTokens.screenHorizontalPadding"))
            assertTrue(text.contains("FinancialDesignTokens.screenTopPadding"))
            assertTrue(text.contains("FinancialDesignTokens.screenBottomNavigationClearance"))
            assertTrue(text.contains("FinancialDesignTokens.sectionSpacing"))
            assertTrue(text.contains("FinancialDesignTokens.cardRadius"))
            assertTrue(text.contains("FinancialDesignTokens.cardPadding"))
        }
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
