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
    onPrimary = V3Surface,
    secondary = V3Teal,
    onSecondary = V3Surface,
    tertiary = V3Success,
    background = V3Navy,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = ColorDarkOutline,
    error = V3Destructive
)

private val LightColorScheme = lightColorScheme(
    primary = V3Primary,
    onPrimary = V3Surface,
    primaryContainer = V3PrimarySoft,
    onPrimaryContainer = V3Navy,
    secondary = V3Success,
    onSecondary = V3Surface,
    secondaryContainer = V3SuccessSoft,
    onSecondaryContainer = V3Navy,
    tertiary = V3Teal,
    onTertiary = V3Surface,
    tertiaryContainer = V3PrimarySoft,
    onTertiaryContainer = V3Navy,
    background = V3Background,
    surface = V3Surface,
    surfaceVariant = V3Muted,
    onBackground = V3Navy,
    onSurface = V3Navy,
    onSurfaceVariant = V3MutedForeground,
    outline = V3Border,
    outlineVariant = V3Border,
    error = V3Destructive,
    errorContainer = V3ErrorSoft
)

private val ColorDarkOutline = Color(0xFF334155)

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
