package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3VisualFidelityV2GuardTest {
    private fun source(path: String) = File("src/main/java/com/example/$path").readText()

    @Test
    fun sharedPremiumPrimitivesUseReferenceLikeCompactGeometry() {
        val visual = source("ui/components/V3ReferenceVisualComponents.kt")

        assertTrue(visual.contains("private val PremiumHeroShape = RoundedCornerShape(22.dp)"))
        assertTrue(visual.contains("private val PremiumCardShape = RoundedCornerShape(18.dp)"))
        assertFalse(visual.contains(".shadow(10.dp"))
        assertFalse(visual.contains(".shadow(12.dp"))
        assertTrue(visual.contains("Modifier.size(68.dp)"))
        assertTrue(visual.contains("Modifier.size(56.dp)"))
    }

    @Test
    fun homeSavingsSummaryIsACompactStripInsteadOfASecondLargeCard() {
        val savings = source("ui/components/SavingsHero.kt")

        assertTrue(savings.contains("CompactSavingsMetric("))
        assertTrue(savings.contains("contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)"))
        assertFalse(savings.contains("style = MaterialTheme.typography.headlineSmall"))
    }

    @Test
    fun primaryNavigationIsIntegratedAndRestrainedInsteadOfHeavyFloatingPill() {
        val nav = source("ui/components/BottomNavBar.kt")

        assertFalse(nav.contains(".padding(start = 16.dp, end = 16.dp"))
        assertTrue(nav.contains("shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)"))
        assertTrue(nav.contains("shadowElevation = 2.dp"))
        assertTrue(nav.contains("heightIn(min = 50.dp)"))
    }
}