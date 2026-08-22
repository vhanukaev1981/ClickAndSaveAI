package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedNavigationContractTest {
    @Test
    fun primaryNavigationHasExactlyTheFrozenV3Destinations() {
        val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()
        val labels = listOf("בית", "חיסכון", "AI", "לתשלום", "פרופיל")
        var previous = -1
        labels.forEach { label ->
            val position = nav.indexOf("\"$label\"")
            assertTrue("missing primary label $label", position >= 0)
            assertTrue("primary navigation order changed at $label", position > previous)
            previous = position
        }
        assertFalse(nav.contains("\"פעילות\""))
        assertFalse(nav.contains("\"אני\""))
        listOf("nav_home", "nav_savings", "nav_ai", "nav_pay", "nav_profile").forEach { tag ->
            assertTrue(nav.contains("\"$tag\""))
        }
        assertTrue(nav.contains("items.forEachIndexed"))
        assertTrue(nav.contains("onTabSelected(index)"))
    }

    @Test
    fun payIsPrimaryWhileActivityIsSecondary() {
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()
        assertTrue(activity.contains("1 -> ProvidersScreen(viewModel)"))
        assertTrue(activity.contains("2 -> AiAssistantScreen(viewModel)"))
        assertTrue(activity.contains("3 -> InvoicesScreen("))
        assertTrue(activity.contains("4 -> ProfileScreen("))
        assertTrue(activity.contains("V3SecondarySurface.ACTIVITY"))
        assertTrue(activity.contains("ActivityScreen(viewModel)"))
        assertTrue(activity.contains("closeSecondarySurface"))
        assertTrue(activity.contains("selectedTab.coerceIn(0, 4)"))
        assertFalse(activity.contains("ProductPreview"))
    }

    @Test
    fun primaryDestinationRemainsViewModelSavedStateWhileSecondaryUiStateIsSaveableLocally() {
        val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()
        assertTrue(viewModel.contains("SavedStateHandle"))
        assertTrue(viewModel.contains("getStateFlow(SELECTED_TAB_KEY, 0)"))
        assertTrue(viewModel.contains("savedStateHandle[SELECTED_TAB_KEY]"))
        assertFalse(viewModel.contains("val selectedTab = MutableStateFlow(0)"))
        assertTrue(activity.contains("rememberSaveable"))
        assertTrue(activity.contains("V3SecondarySurface.ACTIVITY.name"))
    }

    @Test
    fun homeKeepsPayReachableAndActivitySecondary() {
        val home = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        val shell = File("src/main/java/com/example/MainActivity.kt").readText()
        assertTrue(home.contains("onOpenInvoices"))
        assertTrue(home.contains("כל החשבונות") || home.contains("החשבונות שלי"))
        assertTrue(shell.contains("onOpenInvoices = { viewModel.setTab(3) }"))
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
        assertFalse(source.contains("CRM", ignoreCase = true))
        assertFalse(source.contains("הופעל השירות"))
        assertFalse(source.contains("הספק קיבל"))
    }
}
