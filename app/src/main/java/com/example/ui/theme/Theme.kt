package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TechBlueLight,
    onPrimary = SurfaceLight,
    secondary = EmeraldSavings,
    onSecondary = SurfaceLight,
    tertiary = AmberDeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    error = AlertRed
)

private val LightColorScheme = lightColorScheme(
    primary = TechBluePrimary,
    onPrimary = SurfaceLight,
    primaryContainer = V3BlueSoft,
    onPrimaryContainer = BrandNavy,
    secondary = EmeraldSavings,
    onSecondary = SurfaceLight,
    secondaryContainer = V3EmeraldSoft,
    onSecondaryContainer = BrandNavy,
    tertiary = AmberDeal,
    tertiaryContainer = V3AmberSoft,
    background = V3Background,
    surface = SurfaceLight,
    surfaceVariant = V3SurfaceSoft,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    onSurfaceVariant = TextSecondaryLight,
    error = AlertRed,
    errorContainer = V3ErrorSoft
)

@Composable
fun ClickAndSaveTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ClickAndSaveTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
