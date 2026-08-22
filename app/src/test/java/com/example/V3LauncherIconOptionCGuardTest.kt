package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3LauncherIconOptionCGuardTest {
    private fun resource(path: String) = File("src/main/res/$path").readText()

    @Test
    fun ownerApprovedFinalCUsesCleanCsThinOrbitAndSmallMintSparkle() {
        val foreground = resource("drawable/ic_launcher_foreground.xml")

        assertTrue(foreground.contains("#FF22D3EE"))
        assertTrue(foreground.contains("#FF6366F1"))
        assertTrue(foreground.contains("#FFFFFFFF"))
        assertTrue(foreground.contains("#FF5EEAD4"))
        assertTrue(foreground.contains("M78,33 C69,21 49,18 35,27"))
        assertTrue(foreground.contains("M22,62 C25,78 40,88 56,87"))
        assertTrue(foreground.contains("M61,34 C55,28 45,28 39,33"))
        assertTrue(foreground.contains("M75,42 C71,38 64,37 59,40"))
        assertTrue(foreground.contains("M82,27 L83.8,31.2 L88,33"))
        assertFalse(foreground.contains("M85,39 C79,24 63,17 48,19"))
        assertFalse(foreground.contains("strokeWidth=\"9\""))
    }

    @Test
    fun launcherBackgroundAndLegacyFallbackMatchApprovedFinalC() {
        val background = resource("drawable/ic_launcher_background.xml")
        val regular = resource("mipmap-anydpi/ic_launcher.xml")
        val round = resource("mipmap-anydpi/ic_launcher_round.xml")

        assertTrue(background.contains("#071343"))
        assertTrue(regular.contains("#071343"))
        assertTrue(round.contains("#071343"))
        assertTrue(regular.contains("M78,33 C69,21 49,18 35,27"))
        assertTrue(round.contains("M22,62 C25,78 40,88 56,87"))
        assertTrue(regular.contains("M61,34 C55,28 45,28 39,33"))
        assertTrue(round.contains("M75,42 C71,38 64,37 59,40"))
        assertFalse(regular.contains("M85,39 C79,24 63,17 48,19"))
        assertFalse(round.contains("M85,39 C79,24 63,17 48,19"))
    }

    @Test
    fun themedIconKeepsTheCleanCsSilhouetteWithoutDecorativeOrbit() {
        val monochrome = resource("drawable/ic_launcher_monochrome.xml")
        val adaptive = resource("mipmap-anydpi-v26/ic_launcher.xml")

        assertTrue(monochrome.contains("M61,34 C55,28 45,28 39,33"))
        assertTrue(monochrome.contains("M75,42 C71,38 64,37 59,40"))
        assertFalse(monochrome.contains("M78,33 C69,21 49,18 35,27"))
        assertFalse(monochrome.contains("M82,27 L83.8,31.2 L88,33"))
        assertTrue(adaptive.contains("@drawable/ic_launcher_monochrome"))
    }
}
