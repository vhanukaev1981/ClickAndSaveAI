package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.V3Primary

/** Premium savings mark: stacked value rings plus a restrained growth signal. */
@Composable
fun SavingsGlyph(
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = V3Primary,
    contentDescription: String? = null
) {
    val semanticModifier = if (contentDescription.isNullOrBlank()) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(modifier = semanticModifier) {
        val stroke = size.minDimension * 0.075f
        val ringWidth = size.width * 0.58f
        val ringHeight = size.height * 0.20f
        val left = size.width * 0.08f
        val top = size.height * 0.48f

        repeat(3) { index ->
            val y = top + index * size.height * 0.13f
            drawOval(
                color = tint,
                topLeft = Offset(left, y),
                size = Size(ringWidth, ringHeight),
                style = Stroke(width = stroke)
            )
        }

        val start = Offset(size.width * 0.56f, size.height * 0.46f)
        val mid = Offset(size.width * 0.72f, size.height * 0.30f)
        val end = Offset(size.width * 0.90f, size.height * 0.16f)
        drawLine(tint, start, mid, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, mid, end, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(
            tint,
            Offset(end.x - size.width * 0.13f, end.y),
            end,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            tint,
            Offset(end.x, end.y + size.height * 0.13f),
            end,
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
