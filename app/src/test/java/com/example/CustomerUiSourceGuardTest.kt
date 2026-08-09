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

        assertTrue(dashboard.contains("dashboard_screen"))
        assertTrue(bills.contains("invoices_screen"))
        assertTrue(savings.contains("providers_screen"))
        assertTrue(profile.contains("profile_screen"))
    }
}
