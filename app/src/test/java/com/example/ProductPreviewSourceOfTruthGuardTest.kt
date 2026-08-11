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
        assertTrue("Primary screens need the approved compact header hierarchy", preview.contains("private fun ProductScreenHeader("))
        assertTrue("Approved screen header must use compact title typography", preview.contains("style = MaterialTheme.typography.titleLarge"))
        assertTrue("Dashboard must keep a compact savings hero", preview.contains("private fun ProductSavingsHero("))
        assertTrue("Approved primary cards stay on clean white surface", preview.contains("containerColor = MaterialTheme.colorScheme.surface"))
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
