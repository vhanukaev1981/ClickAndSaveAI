package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3FrozenProductContractTest {
    @Test
    fun primaryNavigationMatchesFrozenV3Exactly() {
        val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()
        val labels = listOf("בית", "חיסכון", "AI", "לתשלום", "פרופיל")
        var previous = -1
        labels.forEach { label ->
            val position = nav.indexOf("PremiumNavItem(\"$label\"")
            assertTrue("missing primary label $label", position >= 0)
            assertTrue("primary navigation order changed at $label", position > previous)
            previous = position
        }
        assertFalse(nav.contains("PremiumNavItem(\"פעילות\""))
        assertFalse(nav.contains("PremiumNavItem(\"אני\""))
        listOf("nav_home", "nav_savings", "nav_ai", "nav_pay", "nav_profile").forEach { tag -> assertTrue("missing nav tag $tag", nav.contains("\"$tag\"")) }
        assertTrue(nav.contains("items.forEachIndexed"))
        assertTrue(nav.contains("onTabSelected(index)"))
    }

    @Test
    fun payIsPrimaryAndActivityIsSecondary() {
        val shell = File("src/main/java/com/example/MainActivity.kt").readText()
        val navigation = File("src/main/java/com/example/ui/v3/V3Navigation.kt").readText()
        assertTrue(shell.contains("3 -> InvoicesScreen("))
        assertTrue(shell.contains("4 -> ProfileScreen("))
        assertTrue(shell.contains("V3SecondarySurface.ACTIVITY"))
        assertTrue(shell.contains("ActivityScreen(viewModel)"))
        assertTrue(navigation.contains("PAY(3)"))
        assertTrue(navigation.contains("PROFILE(4)"))
        assertTrue(navigation.contains("ACTIVITY"))
        assertFalse(navigation.contains("ACTIVITY(3)"))
    }

    @Test
    fun profileMatchesFrozenGroupsWithoutTierOrSavingsPromotion() {
        val profile = File("src/main/java/com/example/ui/screens/ProfileScreen.kt").readText()
        listOf("חיבור ונתונים", "פרטיות והרשאות", "פעילות והתראות", "חשבון ואבטחה").forEach { title -> assertTrue("missing profile group $title", profile.contains("V3SectionHeader(\"$title\")")) }
        assertTrue(profile.contains("title = \"פרופיל\"") || profile.contains("Text(\"פרופיל\""))
        listOf("Text(\"Pro\"", "Text(\"Free\"", "שדרוג", "מנוי", "חיסכון שלך").forEach { forbidden -> assertFalse("forbidden profile tier/promo copy: $forbidden", profile.contains(forbidden, ignoreCase = true)) }
    }

    @Test
    fun payScreenIsTruthfulAndSeparatesSavingFromPayment() {
        val pay = File("src/main/java/com/example/ui/screens/InvoicesScreen.kt").readText()
        assertTrue(pay.contains("title = \"לתשלום\"") || pay.contains("Text(\"לתשלום\""))
        assertTrue(pay.contains("מה נכנס לתשלום"))
        assertTrue(pay.contains("האם אפשר לחסוך"))
        assertTrue(pay.contains("מעבר לספק לתשלום"))
        assertTrue(pay.contains("NO_VERIFIED_PAYMENT_TARGET"))
        assertTrue(pay.contains("מועד לתשלום: לא ידוע"))
        assertTrue(pay.contains("Click & Save לא גובה כסף ולא משלם עבורך"))
        assertFalse(pay.contains("paymentUrl"))
        assertFalse(Regex("onClick\\s*=\\s*\\{\\s*}").containsMatchIn(pay))
    }

    @Test
    fun homeShowsAnnualSavingsWheneverAuthoritativeAnnualExists() {
        val home = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue(home.contains("realizedAnnual = summary.realizedAnnual"))
        assertTrue(home.contains("potentialAnnual = summary.potentialAnnual"))
    }

    @Test
    fun presentationContractKeepsTruthBoundariesAndExactModes() {
        val source = File("src/main/java/com/example/ui/v3/V3FinancialPresentation.kt").readText()
        listOf("DIRECT_PLAN_JOIN", "PROVIDER_LEAD_FLOW", "VIEW_ONLY", "NO_VERIFIED_ACTION_TARGET", "DIRECT_INVOICE_PAYMENT", "PROVIDER_PAYMENT_PORTAL", "NO_VERIFIED_PAYMENT_TARGET").forEach { mode -> assertTrue("missing mode $mode", source.contains(mode)) }
        assertTrue(source.contains("monthlyIncrease >= 5.0"))
        assertTrue(source.contains("percentIncrease >= 5.0"))
        assertTrue(source.contains("potentialAnnual"))
        assertTrue(source.contains("realizedAnnual"))
    }

    @Test
    fun billIncreaseCopyNeverClaimsTariffIncrease() {
        val savings = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        assertTrue(savings.contains("השוואה בין חיובים"))
        assertTrue(savings.contains("לא הוכחה לשינוי תעריף"))
        assertFalse(savings.contains("התעריף התייקר"))
    }

    @Test
    fun aiIsProactiveDynamicallyRankedAndGrounded() {
        val ai = File("src/main/java/com/example/ui/screens/AiAssistantScreen.kt").readText()
        val presentation = File("src/main/java/com/example/ui/v3/V3FinancialPresentation.kt").readText()
        assertTrue(ai.contains("authoritativeFinancialHome.collectAsState()"))
        assertTrue(ai.contains("primarySuggestions"))
        assertTrue(ai.contains("secondarySuggestions"))
        assertTrue(presentation.contains("v3RankedAiSuggestions"))
        assertTrue(presentation.contains("hasQualifiedBillIncrease"))
        assertTrue(ai.contains("אין כרגע הצעות אישיות מאומתות"))
    }

    @Test
    fun onboardingStepThreeUsesFrozenTitleAndGmailScopeRemainsReadonly() {
        val onboarding = File("src/main/java/com/example/ui/screens/V3OnboardingContent.kt").readText()
        val shell = File("src/main/java/com/example/MainActivity.kt").readText()
        assertTrue(onboarding.contains("אתה בוחר — וממשיך רק כשמתאים לך"))
        assertTrue(shell.contains("https://www.googleapis.com/auth/gmail.readonly"))
        assertFalse(shell.contains("https://mail.google.com/"))
    }
}
