package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3PremiumVisualRemediationGuardTest {
    private fun source(path: String) = File("src/main/java/com/example/$path").readText()

    @Test
    fun exactFrozenPremiumPaletteIsMappedIntoNativeTokens() {
        val colors = source("ui/theme/Color.kt")
        listOf(
            "V3Background = Color(0xFFF8FAFC)",
            "V3Surface = Color(0xFFFFFFFF)",
            "V3Navy = Color(0xFF0F172A)",
            "V3Primary = Color(0xFF2563EB)",
            "V3Teal = Color(0xFF14B8A6)",
            "V3Success = Color(0xFF00B879)",
            "V3SuccessSoft = Color(0xFFECFDF5)",
            "V3Muted = Color(0xFFF1F5F9)",
            "V3MutedForeground = Color(0xFF64748B)",
            "V3PrimarySoft = Color(0xFFEFF6FF)",
            "V3Border = Color(0xFFE2E8F0)",
            "V3Warning = Color(0xFFF59E0B)",
            "V3WarningSoft = Color(0xFFFFFBEB)",
            "V3Destructive = Color(0xFFEF4444)",
            "V3Aurora1 = Color(0xFF071638)",
            "V3Aurora2 = Color(0xFF17419E)",
            "V3Aurora3 = Color(0xFF0E7490)"
        ).forEach { assertTrue("missing premium token: $it", colors.contains(it)) }
        assertFalse(colors.contains("V3AiViolet = Color(0xFF7C3AED)"))
    }

    @Test
    fun typographyUsesCompactPremiumPhoneHierarchy() {
        val type = source("ui/theme/Type.kt")
        assertTrue(type.contains("fontSize = 22.sp"))
        assertTrue(type.contains("fontSize = 16.sp"))
        assertTrue(type.contains("fontSize = 14.sp"))
        assertTrue(type.contains("fontSize = 11.sp"))
        assertFalse(type.contains("fontSize = 40.sp"))
        assertFalse(type.contains("fontSize = 24.sp"))
    }

    @Test
    fun bottomNavigationIsFloatingPremiumDockAndNeverUsesPigSavingsIcon() {
        val nav = source("ui/components/BottomNavBar.kt")
        assertFalse(nav.contains("NavigationBar("))
        assertFalse(nav.contains("NavigationBarItem("))
        assertFalse(nav.contains("Icons.Filled.Savings"))
        assertFalse(nav.contains("Icons.Outlined.Savings"))
        assertTrue(nav.contains("premium_bottom_nav_dock"))
        assertTrue(nav.contains("RoundedCornerShape(20.dp)"))
        assertTrue(nav.contains("V3PrimarySoft"))
        assertTrue(nav.contains("V3Border"))
        listOf("בית", "חיסכון", "AI", "לתשלום", "פרופיל").forEach {
            assertTrue(nav.contains("\"$it\""))
        }
    }

    @Test
    fun savingsSurfacesUsePremiumNonPigSavingsMetaphor() {
        val savings = source("ui/screens/ProvidersScreen.kt")
        val hero = source("ui/components/SavingsHero.kt")
        assertFalse(savings.contains("Icons.Default.Savings"))
        assertFalse(savings.contains("material.icons.filled.Savings"))
        assertTrue(savings.contains("SavingsGlyph"))
        assertTrue(hero.contains("SavingsGlyph"))
    }

    @Test
    fun sharedPremiumPrimitivesOwnSurfaceGrammar() {
        val status = source("ui/components/V3StatusComponents.kt")
        listOf(
            "V3ScreenHeader",
            "V3Panel",
            "V3PrimaryButton",
            "V3SecondaryButton",
            "V3SettingsGroup",
            "V3SettingsRow"
        ).forEach { assertTrue("missing shared premium primitive $it", status.contains("fun $it(")) }
        assertTrue(status.contains("RoundedCornerShape(20.dp)"))
        assertTrue(status.contains("V3Border"))
    }

    @Test
    fun primaryScreensUseTwentyDpPremiumScreenPaddingAndNoRawDiagnostics() {
        val paths = listOf(
            "ui/screens/DashboardScreen.kt",
            "ui/screens/ProvidersScreen.kt",
            "ui/screens/AiAssistantScreen.kt",
            "ui/screens/InvoicesScreen.kt",
            "ui/screens/ProfileScreen.kt"
        )
        paths.forEach { path ->
            val screen = source(path)
            assertTrue("$path must use premium 20dp horizontal padding", screen.contains("start = 20.dp") && screen.contains("end = 20.dp"))
            assertFalse("$path exposes INTERNAL", screen.contains("INTERNAL"))
            assertFalse("$path exposes recovery diagnostic", screen.contains("Recovery diagnostic", ignoreCase = true))
        }
    }

    @Test
    fun homeFollowsApprovedPremiumInformationHierarchy() {
        val dashboard = source("ui/screens/DashboardScreen.kt")
        val home = source("ui/components/V3HomeComponents.kt")
        assertTrue(dashboard.contains("PRICE_INCREASE_DETECTED"))
        assertTrue(dashboard.contains("\"פעילות אחרונה\""))
        assertTrue(dashboard.contains("\"רק דברים שקרו באמת\""))
        assertTrue(dashboard.contains("\"הסכומים מבוססים על מסמכים"))
        assertTrue(home.contains("Icons.AutoMirrored.Filled.ArrowBack"))
    }

    @Test
    fun savingsSeparatesPotentialProgressAndRealizedStates() {
        val savings = source("ui/screens/ProvidersScreen.kt")
        assertTrue(savings.contains("\"אפשר לחסוך\""))
        assertTrue(savings.contains("\"בתהליך\""))
        assertTrue(savings.contains("\"נחסך בפועל\""))
        assertTrue(savings.contains("savingRealizationState"))
        assertTrue(savings.contains("חיסכון פוטנציאלי אינו חיסכון ממומש"))
    }

    @Test
    fun aiIsProactiveConsumerAssistantRatherThanTechnicalForm() {
        val ai = source("ui/screens/AiAssistantScreen.kt")
        assertTrue(ai.contains("\"מה שכדאי לבדוק עכשיו\""))
        assertTrue(ai.contains("\"עוד דברים שכדאי לבדוק\""))
        assertTrue(ai.contains("\"שיחה\""))
        assertTrue(ai.contains("ai_message_input"))
        assertTrue(ai.contains("ai_send"))
        assertFalse(ai.contains("בדיקת חשבון או מסלול"))
    }

    @Test
    fun billsIncludeSummaryTrustAndUnknownTruthNote() {
        val bills = source("ui/screens/InvoicesScreen.kt")
        assertTrue(bills.contains("\"מסמכים שזוהו\""))
        assertTrue(bills.contains("\"התשלום עצמו מתבצע מול הספק\""))
        assertTrue(bills.contains("״לא ידוע״ אינו אפס"))
        assertTrue(bills.contains("FilterChip("))
        assertTrue(bills.contains("מועד לתשלום: לא ידוע"))
    }

    @Test
    fun screenshotSuiteNamesAllPrimaryPremiumScreensAndCompactRtl() {
        val screenshots = File("src/test/java/com/example/V3PrimaryScreensScreenshotTest.kt").readText()
        listOf(
            "v3-home-premium.png",
            "v3-savings-premium.png",
            "v3-ai-premium.png",
            "v3-bills-premium.png",
            "v3-profile-premium.png",
            "v3-primary-compact-rtl.png"
        ).forEach { assertTrue("missing screenshot fixture $it", screenshots.contains(it)) }
    }
}
