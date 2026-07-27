package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldSavings

@Composable
fun PriceTrendChart(
    priceHistoryCsv: String,
    modifier: Modifier = Modifier,
    lineColor: Color = EmeraldSavings
) {
    val prices = priceHistoryCsv.split(",")
        .mapNotNull { it.trim().toDoubleOrNull() }
        .ifEmpty { listOf(100.0, 95.0, 90.0, 85.0, 79.99) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        val minPrice = prices.minOrNull() ?: 0.0
        val maxPrice = prices.maxOrNull() ?: 1.0
        val range = (maxPrice - minPrice).coerceAtLeast(1.0)

        val width = size.width
        val height = size.height

        val points = prices.mapIndexed { index, price ->
            val x = (index.toFloat() / (prices.size - 1).coerceAtLeast(1)) * width
            val y = height - (((price - minPrice) / range).toFloat() * (height - 12f)) - 6f
            Offset(x, y)
        }

        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Highlight latest point
            val lastPoint = points.last()
            drawCircle(
                color = lineColor,
                radius = 5.dp.toPx(),
                center = lastPoint
            )
        }
    }
}
