package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3LauncherIconOptionCGuardTest {
    private fun resource(path: String) = File("src/main/res/$path").readText()

    @Test
    fun ownerSelectedOptionCUsesPremiumCsOrbitAndMintSparkle() {
        val foreground = resource("drawable/ic_launcher_foreground.xml")

        assertTrue(foreground.contains("#FF22D3EE"))
        assertTrue(foreground.contains("#FF7C3AED"))
        assertTrue(foreground.contains("#FFFFFFFF"))
        assertTrue(foreground.contains("#FF64F7C4"))
        assertTrue(foreground.contains("M85,39 C79,24 63,17 48,19"))
        assertTrue(foreground.contains("M69,35 C62,28 49,26 39,31"))
        assertTrue(foreground.contains("M73,45 C69,41 62,40 57,43"))
        assertFalse(foreground.contains("M54,31 L54,81"))
    }

    @Test
    fun launcherBackgroundAndLegacyFallbackMatchSelectedOptionC() {
        val background = resource("drawable/ic_launcher_background.xml")
        val regular = resource("mipmap-anydpi/ic_launcher.xml")
        val round = resource("mipmap-anydpi/ic_launcher_round.xml")

        assertTrue(background.contains("#071343"))
        assertTrue(regular.contains("#071343"))
        assertTrue(round.contains("#071343"))
        assertTrue(regular.contains("#FF22D3EE"))
        assertTrue(round.contains("#FF7C3AED"))
        assertFalse(regular.contains("M54,31 L54,81"))
        assertFalse(round.contains("M54,31 L54,81"))
    }

    @Test
    fun themedIconKeepsTheCsOrbitWithoutLegacyDollarStem() {
        val monochrome = resource("drawable/ic_launcher_monochrome.xml")
        val adaptive = resource("mipmap-anydpi-v26/ic_launcher.xml")

        assertTrue(monochrome.contains("M85,39 C79,24 63,17 48,19"))
        assertTrue(monochrome.contains("M69,35 C62,28 49,26 39,31"))
        assertTrue(monochrome.contains("M73,45 C69,41 62,40 57,43"))
        assertFalse(monochrome.contains("M54,31 L54,81"))
        assertTrue(adaptive.contains("@drawable/ic_launcher_monochrome"))
    }
}
