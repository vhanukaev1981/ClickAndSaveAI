package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3Pr100ReviewRegressionGuardTest {
    @Test
    fun activitySecondarySurfaceHandlesSystemBack() {
        val shell = File("src/main/java/com/example/MainActivity.kt").readText()
        assertTrue(shell.contains("import androidx.activity.compose.BackHandler"))
        assertTrue(shell.contains("BackHandler(enabled = secondarySurface == V3SecondarySurface.ACTIVITY)"))
        assertTrue(shell.contains("closeSecondarySurface()"))
    }

    @Test
    fun activityGroupingUsesLocalCalendarDateForIsoInstants() {
        val activity = File("src/main/java/com/example/ui/screens/ActivityScreen.kt").readText()
        assertTrue(activity.contains("Instant.parse(timestamp)"))
        assertTrue(activity.contains("ZoneId.systemDefault()"))
        assertFalse(activity.contains("timestamp.take(10)"))
    }

    @Test
    fun inProgressOpportunitiesCannotRestartAction() {
        val savings = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        assertTrue(savings.contains("!opportunity.hasActionInProgress()"))
    }

    @Test
    fun knownZeroSavingsFlowsIntoPremiumHero() {
        val savings = File("src/main/java/com/example/ui/screens/ProvidersScreen.kt").readText()
        val hero = File("src/main/java/com/example/ui/components/V3ReferenceVisualComponents.kt").readText()
        assertTrue(savings.contains("realizedKnownZero = summary.realizedKnownZero"))
        assertTrue(hero.contains("realizedKnownZero: Boolean"))
        assertTrue(hero.contains("realizedKnownZero -> 0.0.asV3Money()"))
    }

    @Test
    fun realizedOpportunitiesAreExcludedFromBillSavingsCta() {
        val pay = File("src/main/java/com/example/ui/screens/InvoicesScreen.kt").readText()
        assertTrue(pay.contains("filter { it.savingRealizationState != \"REALIZED\" }"))
    }
}
