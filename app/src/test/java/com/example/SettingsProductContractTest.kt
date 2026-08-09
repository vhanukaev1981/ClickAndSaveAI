package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProductContractTest {
    private val settingsPath = "src/main/java/com/example/ui/screens/SettingsScreen.kt"

    @Test
    fun numericSavingsInputsRemainBoundedAndTestable() {
        val settings = File(settingsPath).readText()

        assertTrue(settings.contains("goalInput = it.filter(Char::isDigit).take(7)"))
        assertTrue(settings.contains("thresholdInput = it.filter(Char::isDigit).take(7)"))
        assertTrue(settings.contains("monthly_savings_goal"))
        assertTrue(settings.contains("minimum_savings_threshold"))
        assertFalse(settings.contains("KeyboardType.Text"))
    }

    @Test
    fun saveFlowKeepsVisibleFeedbackAndDoesNotAuthorizeProviderAction() {
        val settings = File(settingsPath).readText()

        assertTrue(settings.contains("preferences_saved_confirmation"))
        assertTrue(settings.contains("save_savings_preferences"))
        assertTrue(settings.contains("savedSignature"))
        assertFalse(settings.contains("Toast.makeText"))
        assertTrue(settings.contains("הם לא מבטיחים תוצאה"))
        assertTrue(settings.contains("ההמלצה עדיין תתבסס על התאמה, מחיר ותנאים שניתן לאמת"))
    }

    @Test
    fun unsavedChangesRemainProtectedBeforeLeavingSettings() {
        val settings = File(settingsPath).readText()

        assertTrue(settings.contains("hasUnsavedChanges"))
        assertTrue(settings.contains("showDiscardConfirmation"))
        assertTrue(settings.contains("discard_preferences_changes"))
        assertTrue(settings.contains("keep_editing_preferences"))
        assertTrue(settings.contains("לצאת בלי לשמור?"))
    }

    @Test
    fun allFinancialPreferencePickersKeepStableHooks() {
        val settings = File(settingsPath).readText()

        listOf(
            "preference_electricity",
            "preference_cellular",
            "preference_internet",
            "preference_insurance",
            "preference_streaming"
        ).forEach { tag ->
            assertTrue("Settings lost preference hook $tag", settings.contains(tag))
        }
    }

    @Test
    fun settingsRemainOnSharedFinancialDesignSystem() {
        val settings = File(settingsPath).readText()

        listOf(
            "FinancialDesignTokens.screenHorizontalPadding",
            "FinancialDesignTokens.screenTopPadding",
            "FinancialDesignTokens.screenBottomNavigationClearance",
            "FinancialDesignTokens.sectionSpacing",
            "FinancialDesignTokens.cardRadius",
            "FinancialDesignTokens.cardPadding"
        ).forEach { token ->
            assertTrue("Settings lost shared design token $token", settings.contains(token))
        }
    }
}
