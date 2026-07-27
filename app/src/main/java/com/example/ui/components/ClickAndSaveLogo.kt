package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

/**
 * Click & Save AI Brand Logo Composable
 * Matches the official Brand Identity Guide & Colors:
 * Primary Blue: #2563EB
 * Primary Green: #00C896
 * Dark Navy: #0F172A
 * Tagline: "לחיצה אחת לחיסכון חכם"
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Brand Ring + Click Icon Badge
        Box(
            modifier = Modifier.size(iconSize + 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(iconSize)) {
                val strokeWidth = iconSize.toPx() * 0.12f
                
                // Ring Gradient (Tech Blue #2563EB to Emerald Green #00C896)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(TechBluePrimary, EmeraldSavings, TechBluePrimary)
                    ),
                    startAngle = -90f,
                    sweepAngle = 320f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Inner click circle
                val centerPx = size.width / 2f
                drawCircle(
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else TechBluePrimary.copy(alpha = 0.12f),
                    radius = centerPx * 0.45f
                )
            }

            // Click Hand Indicator Text / Symbol
            Text(
                text = "👆",
                fontSize = (iconSize.value * 0.42f).sp,
                modifier = Modifier.align(Alignment.Center)
            )

            // Shekel Badge Badge in bottom right
            Box(
                modifier = Modifier
                    .size(iconSize * 0.4f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(EmeraldSavings),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "₪",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (iconSize.value * 0.22f).sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Brand Typography Title & Tagline
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Click & ",
                    fontSize = (iconSize.value * 0.48f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isDarkTheme) Color.White else BrandNavy,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Save AI",
                    fontSize = (iconSize.value * 0.48f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = EmeraldSavings,
                    letterSpacing = (-0.5).sp
                )
            }

            if (showTagline) {
                Text(
                    text = "לחיצה אחת לחיסכון חכם",
                    fontSize = (iconSize.value * 0.26f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EmeraldSavings,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}
