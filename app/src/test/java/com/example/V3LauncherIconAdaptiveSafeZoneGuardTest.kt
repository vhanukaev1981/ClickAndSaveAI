package com.example

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3LauncherIconAdaptiveSafeZoneGuardTest {
    private fun resource(path: String) = File("src/main/res/$path").readText()

    @Test
    fun approvedFinalCUsesIntrinsicAndroidSafeZoneGeometry() {
        val foreground = resource("drawable/ic_launcher_foreground.xml")

        // Guard the Samsung regression itself: the approved artwork is drawn
        // intrinsically inside the safe zone instead of filling the 108dp canvas
        // and relying on a launcher-specific compensating scale transform.
        assertTrue(foreground.contains("M78,33 C69,21 49,18 35,27"))
        assertTrue(foreground.contains("M22,62 C25,78 40,88 56,87"))
        assertTrue(foreground.contains("M61,34 C55,28 45,28 39,33"))
        assertTrue(foreground.contains("M75,42 C71,38 64,37 59,40"))

        assertFalse(foreground.contains("M85,39 C79,24 63,17 48,19"))
        assertFalse(foreground.contains("M43,88 C62,94 81,83 88,66"))
        assertFalse(foreground.contains("M82,22 L84.7,29.3 L92,32"))
        assertFalse(foreground.contains("android:scaleX=\"0.72\""))
        assertFalse(foreground.contains("android:scaleY=\"0.72\""))
    }

    @Test
    fun legacyLauncherFallbacksUseTheSameIntrinsicSafeZoneGeometry() {
        val regular = resource("mipmap-anydpi/ic_launcher.xml")
        val round = resource("mipmap-anydpi/ic_launcher_round.xml")

        for (launcher in listOf(regular, round)) {
            assertTrue(launcher.contains("M78,33 C69,21 49,18 35,27"))
            assertTrue(launcher.contains("M22,62 C25,78 40,88 56,87"))
            assertTrue(launcher.contains("M61,34 C55,28 45,28 39,33"))
            assertTrue(launcher.contains("M75,42 C71,38 64,37 59,40"))

            assertFalse(launcher.contains("M85,39 C79,24 63,17 48,19"))
            assertFalse(launcher.contains("M43,88 C62,94 81,83 88,66"))
            assertFalse(launcher.contains("M82,22 L84.7,29.3 L92,32"))
            assertFalse(launcher.contains("android:scaleX=\"0.72\""))
            assertFalse(launcher.contains("android:scaleY=\"0.72\""))
        }
    }
}
