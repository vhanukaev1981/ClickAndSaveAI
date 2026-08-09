package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = TechBlueLight,
    onPrimary = SurfaceLight,
    secondary = EmeraldSavingsLight,
    onSecondary = SurfaceLight,
    tertiary = AmberDealLight,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = SurfaceVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = TechBluePrimary,
    onPrimary = SurfaceLight,
    primaryContainer = HeroBlueSurface,
    onPrimaryContainer = BrandNavy,
    secondary = EmeraldSavings,
    onSecondary = SurfaceLight,
    secondaryContainer = SavingsSurface,
    onSecondaryContainer = EmeraldSavingsDark,
    tertiary = AmberDeal,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = DividerLight
)

@Composable
fun ClickAndSaveTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Brand consistency is deliberate: financial meaning must not change with
    // OEM dynamic colors. Dark mode remains supported for compatibility, while
    // the customer product currently defaults to the polished light experience.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )

    @Suppress("UNUSED_VARIABLE")
    val dynamicColorKeptForApiCompatibility = dynamicColor
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ClickAndSaveTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
