package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePrivacyProductContractTest {
    private val profilePath = "src/main/java/com/example/ui/screens/ProfileScreen.kt"

    @Test
    fun profileKeepsPrivacyAndSavingsAsCustomerFacingDestinations() {
        val profile = File(profilePath).readText()

        assertTrue(profile.contains("profile_screen"))
        assertTrue(profile.contains("open_savings_preferences"))
        assertTrue(profile.contains("open_privacy_connections"))
        assertTrue(profile.contains("פרטיות ושליטה"))
        assertTrue(profile.contains("כל פעולה מול נותן שירות דורשת אישור מפורש שלך"))
        assertFalse(profile.contains("Firebase Auth"))
        assertFalse(profile.contains("App Check"))
    }

    @Test
    fun privacyCopyNeverImpliesClickAndSaveExecutesProviderSwitchOrPayment() {
        val profile = File(profilePath).readText()

        assertTrue(profile.contains("המערכת אינה מבצעת בעצמה מעבר ספק, תשלום או ביטול שירות"))
        assertTrue(profile.contains("לפני העברת פרטים שאישרת לנותן שירות נבקש ממך אישור מפורש"))
        assertFalse(profile.contains("אינה מבצעת מעבר ספק או פעולה כספית ללא אישור מפורש שלך"))
    }

    @Test
    fun signOutAlwaysRequiresExplicitConfirmation() {
        val profile = File(profilePath).readText()

        assertTrue(profile.contains("showSignOutConfirmation"))
        assertTrue(profile.contains("profile_sign_out"))
        assertTrue(profile.contains("confirm_profile_sign_out"))
        assertTrue(profile.contains("cancel_profile_sign_out"))
        assertFalse(profile.contains("onClick = viewModel::signOut"))
    }

    @Test
    fun documentSourceRevocationLivesUnderPrivacyAndRequiresConfirmation() {
        val profile = File(profilePath).readText()

        assertTrue(profile.contains("PrivacyConnectionsScreen("))
        assertTrue(profile.contains("privacy_connections_screen"))
        assertTrue(profile.contains("showDisconnectConfirmation"))
        assertTrue(profile.contains("disconnect_document_source"))
        assertTrue(profile.contains("confirm_disconnect_document_source"))
        assertTrue(profile.contains("cancel_disconnect_document_source"))
        assertFalse(profile.contains("onClick = viewModel::disconnectGmail"))
        assertTrue(profile.contains("המערכת תפסיק לקלוט מסמכים חדשים ממקור זה עד לחיבור מחדש"))
    }

    @Test
    fun profileAndPrivacyRemainOnSharedFinancialDesignSystem() {
        val profile = File(profilePath).readText()

        listOf(
            "FinancialDesignTokens.screenHorizontalPadding",
            "FinancialDesignTokens.screenTopPadding",
            "FinancialDesignTokens.screenBottomNavigationClearance",
            "FinancialDesignTokens.sectionSpacing",
            "FinancialDesignTokens.cardRadius",
            "FinancialDesignTokens.cardPadding"
        ).forEach { token ->
            assertTrue("Profile/Privacy lost shared design token $token", profile.contains(token))
        }
    }
}
