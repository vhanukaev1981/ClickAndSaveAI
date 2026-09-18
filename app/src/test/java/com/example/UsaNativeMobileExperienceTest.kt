package com.example

import com.example.ui.theme.Danger
import com.example.ui.theme.Forest
import com.example.ui.theme.Gold
import com.example.ui.theme.Ink
import com.example.ui.theme.Mint
import com.example.ui.theme.Muted
import com.example.ui.theme.Paper
import com.example.ui.theme.White
import com.example.ui.usa.AppDataMode
import com.example.ui.usa.MonitoringStatus
import com.example.ui.usa.UsaDemoData
import com.example.ui.usa.UsaTab
import com.example.ui.usa.theme.UsaTypography
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsaNativeMobileExperienceTest {

    @Test
    fun designTokensMatchApprovedForestAndPaperPalette() {
        assertEquals(0xFFF8F7F2, Paper.value.toLong() shr 32 and 0xFFFFFFFFL or (Paper.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFF0D4F3C, Forest.value.toLong() shr 32 and 0xFFFFFFFFL or (Forest.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFFD6ECDD, Mint.value.toLong() shr 32 and 0xFFFFFFFFL or (Mint.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFFE8B84A, Gold.value.toLong() shr 32 and 0xFFFFFFFFL or (Gold.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFF18332C, Ink.value.toLong() shr 32 and 0xFFFFFFFFL or (Ink.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFF5F6F69, Muted.value.toLong() shr 32 and 0xFFFFFFFFL or (Muted.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFFFFFFFF, White.value.toLong() shr 32 and 0xFFFFFFFFL or (White.value.toLong() and 0xFFFFFFFFL))
        assertEquals(0xFFA63D2F, Danger.value.toLong() shr 32 and 0xFFFFFFFFL or (Danger.value.toLong() and 0xFFFFFFFFL))
    }

    @Test
    fun typographyScalesFollowApprovedHierarchyWithoutNegativeLetterSpacing() {
        assertEquals(30f, UsaTypography.displayLarge.fontSize.value)
        assertEquals(22f, UsaTypography.titleLarge.fontSize.value)
        assertEquals(18f, UsaTypography.titleMedium.fontSize.value)
        assertEquals(16f, UsaTypography.bodyLarge.fontSize.value)
        assertEquals(11f, UsaTypography.labelSmall.fontSize.value)

        assertTrue(UsaTypography.displayLarge.letterSpacing.value >= 0f)
        assertTrue(UsaTypography.titleLarge.letterSpacing.value >= 0f)
        assertTrue(UsaTypography.bodyLarge.letterSpacing.value >= 0f)
    }

    @Test
    fun primaryNavigationIncludesApprovedFiveDestinations() {
        val expectedTabs = listOf("Home", "Bills", "Savings", "Activity", "Profile")
        val actualTabs = UsaTab.entries.map { it.label }
        assertEquals(expectedTabs, actualTabs)

        assertTrue(actualTabs.contains("Bills"))
        assertFalse(actualTabs.contains("Monitoring"))
    }

    @Test
    fun illustrativeDemoDataConformsStrictlyToApprovedSpecification() {
        val summary = UsaDemoData.summary
        assertEquals("Rivera household", summary.householdName)
        assertEquals(18420.0, summary.monitoredAnnual, 0.01)
        assertEquals(2364.0, summary.potentialAnnual, 0.01)
        assertEquals(1, summary.needsReviewCount)
        assertEquals(8, summary.opportunitiesCount)
        assertEquals(2, summary.renewalsCount)

        val opp = UsaDemoData.primaryOpportunity
        assertEquals("Internet bill increased after promo ended", opp.title)
        assertEquals(94.0, opp.currentMonthly, 0.01)
        assertEquals(62.0, opp.potentialMonthly, 0.01)
        assertEquals(384.0, opp.annualSavings, 0.01)

        val trueCost = opp.trueCost
        assertEquals(55.0, trueCost.monthlyService, 0.01)
        assertEquals(7.0, trueCost.equipment, 0.01)
        assertEquals(0.0, trueCost.activation, 0.01)
        assertEquals(0.0, trueCost.terminationCost, 0.01)
        assertEquals(0.0, trueCost.lostBundleDiscount, 0.01)
        assertEquals(744.0, trueCost.firstYearTotal, 0.01)
        assertEquals("$78/mo", trueCost.postPromoPrice)
    }

    @Test
    fun decisionFlowProvidesExplicitChoicesAndNeverClaimsAutomaticSwitching() {
        val opportunityFlowSource = File("src/main/java/com/example/ui/usa/UsaOpportunityFlow.kt").readText()
        val componentsSource = File("src/main/java/com/example/ui/usa/UsaComponents.kt").readText()

        assertFalse(opportunityFlowSource.contains("Buy now", ignoreCase = true))
        assertFalse(opportunityFlowSource.contains("Switch automatically", ignoreCase = true))

        assertTrue(componentsSource.contains("Open provider handoff"))
        assertTrue(componentsSource.contains("Keep current plan"))
        assertTrue(componentsSource.contains("Remind me later"))
        assertTrue(componentsSource.contains("Cancel"))
        assertTrue(componentsSource.contains("Compensation must not determine how opportunities are presented or ranked"))

        assertTrue(opportunityFlowSource.contains("Opening a provider handoff is not approval to switch"))
    }

    @Test
    fun savingsStagesSeparatePotentialExpectedAndVerified() {
        val savingsSource = File("src/main/java/com/example/ui/usa/UsaSavingsScreen.kt").readText()
        assertTrue(savingsSource.contains("Potential, expected, and verified savings are kept separate"))
        assertTrue(savingsSource.contains("SavingsStageSummary"))
        assertTrue(savingsSource.contains("Stay recommended"))
        assertTrue(savingsSource.contains("Mobile plan checked"))
        assertTrue(savingsSource.contains("Keep current plan"))
    }

    @Test
    fun initialDiscoveryAvoidsFakeScanPercentage() {
        val onboardSource = File("src/main/java/com/example/ui/usa/UsaOnboarding.kt").readText()
        assertTrue(onboardSource.contains("No percentage is shown because completion time depends on the connected account"))
        assertFalse(onboardSource.contains("%"))
        assertTrue(onboardSource.contains("Building your first household picture"))
    }

    @Test
    fun mainActivityWiredWithUsaAppShellAndLtrDirection() {
        val mainActivitySource = File("src/main/java/com/example/MainActivity.kt").readText()
        val usaAppShellSource = File("src/main/java/com/example/ui/usa/UsaAppShell.kt").readText()

        assertTrue(mainActivitySource.contains("com.example.ui.usa.theme.UsaTheme"))
        assertTrue(mainActivitySource.contains("com.example.ui.usa.UsaAppShell()"))

        assertTrue(usaAppShellSource.contains("LocalLayoutDirection provides LayoutDirection.Ltr"))
    }
}
