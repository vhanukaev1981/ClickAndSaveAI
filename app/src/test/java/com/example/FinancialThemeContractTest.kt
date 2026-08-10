package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialThemeContractTest {
    private val colorPath = "src/main/java/com/example/ui/theme/Color.kt"
    private val themePath = "src/main/java/com/example/ui/theme/Theme.kt"
    private val typePath = "src/main/java/com/example/ui/theme/Type.kt"

    @Test
    fun northStarSemanticPaletteRemainsDistinct() {
        val colors = File(colorPath).readText()

        assertTrue(colors.contains("val BrandNavy = Color("))
        assertTrue(colors.contains("val TechBluePrimary = Color("))
        assertTrue(colors.contains("val EmeraldSavings = Color("))
        assertTrue(colors.contains("val AmberDeal = Color("))
        assertTrue(colors.contains("val AlertRed = Color("))
    }

    @Test
    fun verifiedSavingsKeepsDedicatedGreenSemanticPalette() {
        val colors = File(colorPath).readText()

        assertTrue(colors.contains("val EmeraldSavings = Color("))
        assertTrue(colors.contains("val EmeraldSavingsLight = Color("))
        assertTrue(colors.contains("val EmeraldSavingsDark = Color("))
        assertTrue(colors.contains("val SavingsSurface = Color("))
    }

    @Test
    fun customerThemeDefaultsToStableLightFinancialExperience() {
        val theme = File(themePath).readText()

        assertTrue(theme.contains("darkTheme: Boolean = false"))
        assertTrue(theme.contains("dynamicColor: Boolean = false"))
        assertTrue(theme.contains("secondary = EmeraldSavings"))
        assertTrue(theme.contains("secondaryContainer = SavingsSurface"))
        assertTrue(theme.contains("val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme"))
        assertTrue(theme.contains("dynamicColorKeptForApiCompatibility"))
    }

    @Test
    fun financialTypographyKeepsMoneyAndPrimaryDecisionsVisuallyProminent() {
        val type = File(typePath).readText()

        assertTrue(type.contains("displaySmall = TextStyle("))
        assertTrue(type.contains("fontSize = 36.sp"))
        assertTrue(type.contains("headlineSmall = TextStyle("))
        assertTrue(type.contains("titleLarge = TextStyle("))
        assertTrue(type.contains("labelMedium = TextStyle("))
    }
}
