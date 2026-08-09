package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamBAccessibilityContractTest {
    private val navPath = "src/main/java/com/example/ui/components/BottomNavBar.kt"
    private val profilePath = "src/main/java/com/example/ui/screens/ProfileScreen.kt"
    private val settingsPath = "src/main/java/com/example/ui/screens/SettingsScreen.kt"
    private val billsPath = "src/main/java/com/example/ui/screens/InvoicesScreen.kt"

    @Test
    fun bottomNavigationKeepsSemanticTabsAndMinimumTouchTargets() {
        val nav = File(navPath).readText()
        assertTrue(nav.contains("Role.Tab"))
        assertTrue(nav.contains("this.selected = selected"))
        assertTrue(nav.contains("this.contentDescription = contentDescription"))
        assertTrue(nav.contains("FinancialDesignTokens.minimumTouchTarget"))
        listOf("nav_dashboard", "nav_invoices", "nav_savings", "nav_profile").forEach { tag ->
            assertTrue("Navigation lost accessible destination $tag", nav.contains(tag))
        }
    }

    @Test
    fun backNavigationKeepsAccessibleDescriptionsAndStableHooks() {
        val profile = File(profilePath).readText()
        val settings = File(settingsPath).readText()
        assertTrue(profile.contains("privacy_back"))
        assertTrue(profile.contains("contentDescription = \"חזרה\""))
        assertTrue(settings.contains("settings_back"))
        assertTrue(settings.contains("contentDescription = \"חזרה\""))
    }

    @Test
    fun destructiveBillActionKeepsAccessibleDeleteLabel() {
        val bills = File(billsPath).readText()
        assertTrue(bills.contains("delete_bill_${'$'}{invoice.id}"))
        assertTrue(bills.contains("contentDescription = \"מחק חשבון\""))
        assertTrue(bills.contains("confirm_delete_bill"))
        assertTrue(bills.contains("cancel_delete_bill"))
    }

    @Test
    fun destructiveAccountAndConnectionActionsStayExplicitlyConfirmable() {
        val profile = File(profilePath).readText()
        listOf(
            "profile_sign_out",
            "confirm_profile_sign_out",
            "cancel_profile_sign_out",
            "disconnect_document_source",
            "confirm_disconnect_document_source",
            "cancel_disconnect_document_source"
        ).forEach { tag -> assertTrue("Profile lost confirmation hook $tag", profile.contains(tag)) }
    }
}
