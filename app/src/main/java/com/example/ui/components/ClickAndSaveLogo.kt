package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

/**
 * Click&SaveAI brand mark.
 *
 * The surrounding app is RTL, but the canonical Latin wordmark is an LTR brand
 * token and must never be reordered by the Hebrew layout direction.
 */
@Composable
fun ClickAndSaveLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 44.dp,
    showTagline: Boolean = true,
    isDarkTheme: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(iconSize + 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                val strokeWidth = iconSize.toPx() * 0.11f
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(TechBluePrimary, EmeraldSavings, TechBluePrimary)
                    ),
                    startAngle = -90f,
                    sweepAngle = 320f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawCircle(
                    color = if (isDarkTheme) {
                        Color.White.copy(alpha = 0.12f)
                    } else {
                        TechBluePrimary.copy(alpha = 0.08f)
                    },
                    radius = size.width * 0.22f
                )
            }

            Text(
                text = "👆",
                fontSize = (iconSize.value * 0.40f).sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = "Click&SaveAI",
                    fontSize = (iconSize.value * 0.50f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDarkTheme) Color.White else BrandNavy,
                    letterSpacing = (-0.4).sp
                )
            }

            if (showTagline) {
                Text(
                    text = "לחיצה אחת לחיסכון חכם",
                    fontSize = (iconSize.value * 0.25f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldSavings
                )
            }
        }
    }
}
