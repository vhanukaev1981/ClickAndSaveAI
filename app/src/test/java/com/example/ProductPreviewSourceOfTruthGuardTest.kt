package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductPreviewSourceOfTruthGuardTest {
    private val preview = File("src/main/java/com/example/ui/screens/ProductPreviewScreens.kt").readText()
    private val activity = File("src/main/java/com/example/MainActivity.kt").readText()
    private val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()
    private val logo = File("src/main/java/com/example/ui/components/ClickAndSaveLogo.kt").readText()
    private val tokens = File("src/main/java/com/example/ui/theme/FinancialDesignTokens.kt").readText()
    private val typography = File("src/main/java/com/example/ui/theme/Type.kt").readText()
    private val colors = File("src/main/java/com/example/ui/theme/Color.kt").readText()

    @Test
    fun primaryNavigationRoutesToP0ProductPreview() {
        listOf(
            "ProductDashboardScreen(",
            "ProductBillsScreen(",
            "ProductSavingsScreen(",
            "ProductMeScreen("
        ).forEach { route ->
            assertTrue("MainActivity must route to $route", activity.contains(route))
        }

        assertFalse("Legacy DashboardScreen must not be the primary route", activity.contains("DashboardScreen(viewModel"))
        assertFalse("Legacy InvoicesScreen must not be the primary route", activity.contains("InvoicesScreen(viewModel"))
        assertFalse("Legacy ProvidersScreen must not be the primary route", activity.contains("ProvidersScreen(viewModel"))
        assertFalse("Legacy ProfileScreen must not be the primary route", activity.contains("ProfileScreen(viewModel"))
    }

    @Test
    fun headerUsesApprovedLightBrandAndAndroidSafeArea() {
        assertTrue(activity.contains("statusBarsPadding()"))
        assertTrue(activity.contains("ClickAndSaveLogo("))
        assertTrue(activity.contains("isDarkTheme = false"))
        assertTrue("Approved header stays compact", activity.contains("iconSize = 28.dp"))
        assertTrue("Approved header stays compact", activity.contains("vertical = 4.dp"))
    }

    @Test
    fun brandWordmarkStaysCanonicalAndLtrInsideHebrewUi() {
        assertTrue("Brand wordmark must be forced LTR inside the RTL app", logo.contains("LocalLayoutDirection"))
        assertTrue("Brand wordmark must be forced LTR inside the RTL app", logo.contains("LayoutDirection.Ltr"))
        assertTrue("Canonical brand text must be Click&SaveAI", logo.contains("text = \"Click&SaveAI\""))
        assertFalse("Do not split the wordmark into RTL-reorderable fragments", logo.contains("text = \"Click & \""))
        assertFalse("Do not split the wordmark into RTL-reorderable fragments", logo.contains("text = \"Save AI\""))
    }

    @Test
    fun approvedCompactVisualHierarchyIsSharedAcrossPrimaryScreens() {
        assertTrue(tokens.contains("val screenTopPadding: Dp = 12.dp"))
        assertTrue(tokens.contains("val sectionSpacing: Dp = 10.dp"))
        assertTrue(tokens.contains("val compactSpacing: Dp = 4.dp"))
        assertTrue(tokens.contains("val heroPadding: Dp = 16.dp"))
        assertTrue(tokens.contains("val compactCardPadding: Dp = 12.dp"))
        assertTrue("Touch targets must remain accessible", tokens.contains("val minimumTouchTarget: Dp = 48.dp"))
        assertTrue("Compact screen titles must use the approved 20sp level", typography.contains("fontSize = 20.sp"))
        assertTrue("Dashboard must keep its truthful savings hero", preview.contains("private fun ProductSavingsHero("))
        assertTrue("Approved primary cards stay on clean white surface", preview.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertFalse("Lavender cast is outside the approved Light Premium direction", colors.contains("0xFFEEF2F8"))
        assertTrue("Savings surface must remain subtle; green is for verified values", colors.contains("val SavingsSurface = Color(0xFFF4FBF8)"))
    }

    @Test
    fun productPreviewIsSavingsFirstNotExpenseTrackerFirst() {
        listOf(
            "המצב הפיננסי שלך",
            "לאן הכסף הולך",
            "monthly_savings_goal",
            "add_manual_bill",
            "save_manual_bill"
        ).forEach { forbidden ->
            assertFalse("P0 preview contains legacy expense/budget framing: $forbidden", preview.contains(forbidden))
        }

        assertFalse(nav.contains("המצב הפיננסי"))
        assertTrue(preview.contains("החיסכון שלך, בלי לנחש"))
        assertTrue(preview.contains("הזדמנויות החיסכון שלך"))
        assertTrue(preview.contains("חיסכון מאומת"))
    }

    @Test
    fun verifiedSavingsUseSavingsGreenAndAreNotSynthesized() {
        assertTrue(preview.contains("EmeraldSavings"))
        assertTrue(preview.contains("potentialMonthlySaving?.takeIf(::positiveFinite)"))
        assertFalse("Annual savings must never be synthesized as monthly x 12", preview.contains("* 12.0"))
        assertFalse("Annual savings must never be synthesized as monthly x 12", preview.contains("*12.0"))
    }

    @Test
    fun providerActionRequiresExplicitConsentAndDoesNotClaimSwitchingOrDeliveryWithoutEvidence() {
        assertTrue(preview.contains("אני מאשר/ת במפורש את העברת פרטי הקשר לספק"))
        assertTrue(preview.contains("Click&SaveAI לא מבצעת את המעבר"))
        assertTrue(preview.contains("רק לאחר שתהיה למערכת הוכחת מסירה בפועל"))
        assertTrue(preview.contains("recordSavingsActionStarted("))
        assertTrue(preview.contains("acceptSavingsOpportunity("))
    }

    @Test
    fun billsDoNotPretendToProcessPayments() {
        assertTrue(preview.contains("תשלום נשאר אצל הספק"))
        assertTrue(preview.contains("Click&SaveAI אינה שומרת כרטיסי אשראי ואינה סולקת תשלומים"))
        assertTrue(preview.contains("כתובת תשלום רשמית של הספק"))
        assertFalse("Preview must not contain a fake provider-payment URL", preview.contains("https://pay."))
    }

    @Test
    fun loadingCopyDoesNotUseFakePercentageMilestones() {
        listOf("25%", "50%", "75%", "100%", "scanProgress", "fakeProgress").forEach { forbidden ->
            assertFalse("P0 preview must not render fake progress marker $forbidden", preview.contains(forbidden))
        }
        assertTrue(preview.contains("אין כאן אחוזי התקדמות מדומים"))
    }
}
