package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V3LauncherIconAdaptiveSafeZoneGuardTest {
    private fun resource(path: String) = File("src/main/res/$path").readText()

    @Test
    fun approvedOptionCForegroundIsScaledIntoAndroidAdaptiveSafeZone() {
        val foreground = resource("drawable/ic_launcher_foreground.xml")

        assertTrue(foreground.contains("<group"))
        assertTrue(foreground.contains("android:pivotX=\"54\""))
        assertTrue(foreground.contains("android:pivotY=\"54\""))
        assertTrue(foreground.contains("android:scaleX=\"0.72\""))
        assertTrue(foreground.contains("android:scaleY=\"0.72\""))
    }

    @Test
    fun legacyLauncherFallbacksUseTheSameReducedOptionCForegroundScale() {
        val regular = resource("mipmap-anydpi/ic_launcher.xml")
        val round = resource("mipmap-anydpi/ic_launcher_round.xml")

        for (launcher in listOf(regular, round)) {
            assertTrue(launcher.contains("android:pivotX=\"54\""))
            assertTrue(launcher.contains("android:pivotY=\"54\""))
            assertTrue(launcher.contains("android:scaleX=\"0.72\""))
            assertTrue(launcher.contains("android:scaleY=\"0.72\""))
        }
    }
}
