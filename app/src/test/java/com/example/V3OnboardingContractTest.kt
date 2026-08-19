package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3OnboardingContractTest {
    @Test
    fun onboardingRemainsNativeAndPrivacyFirst() {
        val source = File("src/main/java/com/example/ui/screens/V3OnboardingContent.kt")
        assertTrue("V3 onboarding content must exist", source.exists())
        val text = if (source.exists()) source.readText() else ""

        assertTrue(text.contains("הכסף שלך יכול לעבוד חכם יותר"))
        assertTrue(text.contains("אנחנו מחפשים — אתה מחליט"))
        assertTrue(text.contains("מחברים Gmail בצורה מאובטחת"))
        assertTrue(text.contains("קריאה בלבד"))
        assertFalse(text.contains("startGmailConnect"))
        assertFalse(text.contains("GOOGLE_MAIL_APP_USER_CONNECTOR_CLIENT_API_KEY"))
        assertFalse(text.contains("WebView"))
        assertFalse(text.contains("Capacitor"))
    }

    @Test
    fun onboardingProgressionUsesSavedStateAndExistingAuthCallbacksOnly() {
        val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()
        val dashboard = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()

        assertTrue(viewModel.contains("val onboardingStep: StateFlow<Int>"))
        assertTrue(viewModel.contains("savedStateHandle.getStateFlow(ONBOARDING_STEP_KEY, 0)"))
        assertTrue(viewModel.contains("fun nextOnboardingStep()"))
        assertTrue(viewModel.contains("fun resetOnboarding()"))
        assertFalse(viewModel.contains("DataStore"))
        assertFalse(viewModel.contains("SharedPreferences"))

        assertTrue(dashboard.contains("viewModel.onboardingStep.collectAsState()"))
        assertTrue(dashboard.contains("V3OnboardingContent("))
        assertTrue(dashboard.contains("onGoogleSignIn = onGoogleSignIn"))
        assertTrue(dashboard.contains("onConnectGmail = { showGmailConsent = true }"))
    }

    @Test
    fun firstSyncCopyStaysConservativeAndStateDriven() {
        val dashboard = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()

        assertTrue(dashboard.contains("FinancialSyncState.CheckingConnection"))
        assertTrue(dashboard.contains("מחברים את החשבון"))
        assertTrue(dashboard.contains("FinancialSyncState.Recovering"))
        assertTrue(dashboard.contains("מסדרים את התמונה שלך"))
        assertTrue(dashboard.contains("אנחנו מעדכנים את התמונה שלך"))
        assertTrue(dashboard.contains("gmailSyncStep.takeIf(String::isNotBlank)"))
    }
}
