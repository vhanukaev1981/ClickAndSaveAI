package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3DeviceObservedVisualRemediationGuardTest {
    private fun source(path: String) = File("src/main/java/com/example/$path").readText()

    @Test
    fun forcedLightAppUsesExplicitLightStatusBarIcons() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("SystemBarStyle.light"))
        assertTrue(main.contains("statusBarStyle"))
    }

    @Test
    fun savingsUsesOneDominantPremiumHeroInsteadOfStackedGradientHeroes() {
        val savings = source("ui/screens/ProvidersScreen.kt")
        assertTrue(savings.contains("V3SavingsDashboardHero("))
        assertFalse(savings.contains("V3GradientHeader("))
    }

    @Test
    fun aiSecondaryPromptsUseCompactGridAndSanitizeTechnicalCopyAtPresentationBoundary() {
        val ai = source("ui/screens/AiAssistantScreen.kt")
        assertTrue(ai.contains("AiSuggestionGrid("))
        assertTrue(ai.contains("consumerAiError"))
        assertTrue(ai.contains("consumerAiMessage"))
        assertFalse(ai.contains("Text(errorMessage"))
        assertFalse(ai.contains("Text(message.text"))
    }

    @Test
    fun profileConnectionCardDoesNotWrapTheEmailIntoTheHebrewStatusSentence() {
        val profile = source("ui/screens/ProfileScreen.kt")
        assertFalse(profile.contains("Gmail מחובר · ${'$'}{connection.email}"))
    }

    @Test
    fun mixedHebrewLatinGreetingUsesDirectionalIsolation() {
        val home = source("ui/screens/DashboardScreen.kt")
        assertTrue(home.contains("premiumGreeting"))
        assertTrue(home.contains("\\u2066"))
        assertTrue(home.contains("\\u2069"))
    }
}
