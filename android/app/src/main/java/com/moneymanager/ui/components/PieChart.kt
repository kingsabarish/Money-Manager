package com.moneymanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/** A single wedge of a [PieChart]: its share of the whole (0f..1f) and its color. */
data class PieSlice(val fraction: Float, val color: Color)

/**
 * A donut chart: each [slices] wedge is drawn as an arc proportional to its
 * fraction. The ring leaves a hole in the middle for a modern look; a thin gap
 * separates adjacent wedges.
 */
@Composable
fun PieChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    ringWidthFraction: Float = 0.22f,
    gapDegrees: Float = 2f,
) {
    Canvas(modifier = modifier) {
        val diameter = min(size.width, size.height)
        val stroke = diameter * ringWidthFraction
        val inset = stroke / 2f
        val topLeft =
            Offset(
                x = (size.width - diameter) / 2f + inset,
                y = (size.height - diameter) / 2f + inset,
            )
        val arcSize = Size(diameter - stroke, diameter - stroke)

        // A single full-circle slice can't have a gap or it renders as an open ring.
        val drawGap = slices.size > 1
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = slice.fraction * 360f
            if (sweep <= 0f) return@forEach
            val gap = if (drawGap) gapDegrees else 0f
            drawArc(
                color = slice.color,
                startAngle = startAngle + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
            startAngle += sweep
        }
    }
}
