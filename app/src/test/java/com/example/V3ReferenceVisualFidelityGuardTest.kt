package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3ReferenceVisualFidelityGuardTest {
    private fun source(path: String) = File("src/main/java/com/example/$path").readText()

    @Test
    fun approvedReferenceHasDedicatedPremiumVisualPrimitives() {
        val file = File("src/main/java/com/example/ui/components/V3ReferenceVisualComponents.kt")
        assertTrue("approved reference requires dedicated premium visual primitives", file.exists())
        val visual = file.readText()
        listOf(
            "fun V3GradientHeader(",
            "fun V3FinancialOverviewCard(",
            "fun V3SavingsDashboardHero(",
            "fun V3AiExperienceHero(",
            "fun V3BillVisualCard(",
            "fun V3ProfileHero("
        ).forEach { assertTrue("missing reference primitive $it", visual.contains(it)) }
    }

    @Test
    fun premiumPaletteIncludesRoyalBlueIndigoAndVioletGradientStops() {
        val colors = source("ui/theme/Color.kt")
        listOf(
            "V3GradientBlue",
            "V3GradientIndigo",
            "V3GradientViolet",
            "V3GradientBlueSoft",
            "V3GradientVioletSoft"
        ).forEach { assertTrue("missing approved reference color $it", colors.contains(it)) }
    }

    @Test
    fun allFivePrimaryScreensAdoptReferenceVisualLanguage() {
        val home = source("ui/screens/DashboardScreen.kt")
        val savings = source("ui/screens/ProvidersScreen.kt")
        val ai = source("ui/screens/AiAssistantScreen.kt")
        val bills = source("ui/screens/InvoicesScreen.kt")
        val profile = source("ui/screens/ProfileScreen.kt")

        assertTrue(home.contains("V3GradientHeader("))
        assertTrue(home.contains("V3FinancialOverviewCard("))
        assertTrue(savings.contains("V3SavingsDashboardHero("))
        assertTrue(ai.contains("V3AiExperienceHero("))
        assertTrue(bills.contains("V3GradientHeader("))
        assertTrue(bills.contains("V3BillVisualCard("))
        assertTrue(profile.contains("V3ProfileHero("))
    }

    @Test
    fun referenceRemediationNeverHardcodesConceptDemoData() {
        val paths = listOf(
            "ui/screens/DashboardScreen.kt",
            "ui/screens/ProvidersScreen.kt",
            "ui/screens/AiAssistantScreen.kt",
            "ui/screens/InvoicesScreen.kt",
            "ui/screens/ProfileScreen.kt",
            "ui/components/V3ReferenceVisualComponents.kt"
        )
        val forbidden = listOf("19,860", "28,940", "7,320", "HOT MOBILE", "312.40", "98.70")
        paths.filter { File("src/main/java/com/example/$it").exists() }.forEach { path ->
            val text = source(path)
            forbidden.forEach { token ->
                assertFalse("$path must not hardcode concept-only demo value $token", text.contains(token))
            }
        }
    }

    @Test
    fun aiReferenceExperienceDoesNotExposeBackendImplementationLanguage() {
        val ai = source("ui/screens/AiAssistantScreen.kt")
        assertFalse(ai.contains("Backend"))
        assertFalse(ai.contains("מקור רשמי"))
    }
}
