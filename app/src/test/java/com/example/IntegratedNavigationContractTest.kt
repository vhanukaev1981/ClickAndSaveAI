package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedNavigationContractTest {
    @Test
    fun primaryNavigationHasExactlyTheApprovedFiveIntegratedDestinations() {
        val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()

        assertTrue(nav.contains("Text(\"בית\")"))
        assertTrue(nav.contains("Text(\"חשבונות\")"))
        assertTrue(nav.contains("Text(\"חיסכון\")"))
        assertTrue(nav.contains("Text(\"פעילות\")"))
        assertTrue(nav.contains("Text(\"אני\")"))

        assertTrue(nav.contains("onTabSelected(0)"))
        assertTrue(nav.contains("onTabSelected(1)"))
        assertTrue(nav.contains("onTabSelected(2)"))
        assertTrue(nav.contains("onTabSelected(3)"))
        assertTrue(nav.contains("onTabSelected(4)"))
    }

    @Test
    fun activityIsARealIntegratedDestinationAndAllPrimaryRoutesReceiveTheSameViewModel() {
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()

        assertTrue(activity.contains("import com.example.ui.screens.ActivityScreen"))
        assertTrue(activity.contains("3 -> ActivityScreen(viewModel)"))
        assertTrue(activity.contains("4 -> ProfileScreen("))
        assertTrue(activity.contains("selectedTab.coerceIn(0, 4)"))
        assertFalse(activity.contains("3, 4 -> ProfileScreen"))
        assertFalse(activity.contains("ProductPreview"))
    }

    @Test
    fun activityScreenConsumesAuthoritativeLedgerAndDoesNotInferHistoryFromCurrentUiState() {
        val screen = File("src/main/java/com/example/ui/screens/ActivityScreen.kt")
        assertTrue(screen.exists())
        val source = if (screen.exists()) screen.readText() else ""

        assertTrue(source.contains("FinancialActivityEvent"))
        assertTrue(source.contains("activityOrNull") || source.contains("authoritativeFinancialActivity"))
        assertFalse(source.contains("title = \"הסנכרון הושלם\""))
        assertFalse(source.contains("state.latestScan.invoices.isNotEmpty()"))
        assertFalse(source.contains("lead", ignoreCase = true))
        assertFalse(source.contains("CRM", ignoreCase = true))
        assertFalse(source.contains("הופעל השירות"))
        assertFalse(source.contains("הספק קיבל"))
    }
}
