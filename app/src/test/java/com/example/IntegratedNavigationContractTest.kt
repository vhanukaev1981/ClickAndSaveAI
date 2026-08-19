package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegratedNavigationContractTest {
    @Test
    fun primaryNavigationHasExactlyTheApprovedV3Destinations() {
        val nav = File("src/main/java/com/example/ui/components/BottomNavBar.kt").readText()

        assertTrue(nav.contains("Text(\"בית\")"))
        assertTrue(nav.contains("Text(\"חיסכון\")"))
        assertTrue(nav.contains("Text(\"AI\")"))
        assertTrue(nav.contains("Text(\"פעילות\")"))
        assertTrue(nav.contains("Text(\"אני\")"))
        assertFalse(nav.contains("Text(\"חשבונות\")"))

        assertTrue(nav.contains("onTabSelected(0)"))
        assertTrue(nav.contains("onTabSelected(1)"))
        assertTrue(nav.contains("onTabSelected(2)"))
        assertTrue(nav.contains("onTabSelected(3)"))
        assertTrue(nav.contains("onTabSelected(4)"))
    }

    @Test
    fun primaryRoutesUseSavingsAndAiWhileInvoicesBecomeSecondary() {
        val activity = File("src/main/java/com/example/MainActivity.kt").readText()

        assertTrue(activity.contains("import com.example.ui.screens.AiAssistantScreen"))
        assertTrue(activity.contains("1 -> ProvidersScreen(viewModel)"))
        assertTrue(activity.contains("2 -> AiAssistantScreen(viewModel)"))
        assertTrue(activity.contains("3 -> ActivityScreen(viewModel)"))
        assertTrue(activity.contains("4 -> ProfileScreen("))
        assertFalse(activity.contains("1 -> InvoicesScreen("))
        assertTrue(activity.contains("V3SecondarySurface.INVOICES"))
        assertTrue(activity.contains("viewModel.closeSecondarySurface"))
        assertTrue(activity.contains("selectedTab.coerceIn(0, 4)"))
        assertFalse(activity.contains("ProductPreview"))
    }

    @Test
    fun selectedPrimaryAndSecondaryDestinationsAreSavedStateOwned() {
        val viewModel = File("src/main/java/com/example/ui/MainViewModel.kt").readText()
        assertTrue(viewModel.contains("SavedStateHandle"))
        assertTrue(viewModel.contains("getStateFlow(SELECTED_TAB_KEY, 0)"))
        assertTrue(viewModel.contains("savedStateHandle[SELECTED_TAB_KEY]"))
        assertTrue(viewModel.contains("SECONDARY_SURFACE_KEY"))
        assertTrue(viewModel.contains("fun openInvoices()"))
        assertTrue(viewModel.contains("fun closeSecondarySurface()"))
        assertFalse(viewModel.contains("val selectedTab = MutableStateFlow(0)"))
    }

    @Test
    fun homeKeepsInvoicesReachableWithoutMakingThemPrimaryNavigation() {
        val home = File("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue(home.contains("onOpenInvoices"))
        assertTrue(home.contains("כל החשבונות") || home.contains("החשבונות שלי"))
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
