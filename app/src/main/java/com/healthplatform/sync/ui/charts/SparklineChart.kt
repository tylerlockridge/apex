package com.healthplatform.sync.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Mini sparkline chart with no axes or labels.
 * Points are normalized 0..1 and rendered as a smooth line.
 * Animates left-to-right on first composition.
 */
@Composable
fun SparklineChart(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(points) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )
    }

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val strokeWidthPx = 2.5f

        // Determine how many points to draw based on animation progress
        val visibleCount = (points.size * progress.value).toInt().coerceAtLeast(2)
        val visiblePoints = points.take(visibleCount)

        val path = Path()
        visiblePoints.forEachIndexed { index, value ->
            val x = if (visiblePoints.size == 1) 0f
            else w * index / (visiblePoints.size - 1)
            // value is 0..1 where 0 = bottom, 1 = top; invert for canvas coords
            val y = h * (1f - value.coerceIn(0f, 1f))

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                // Simple cubic bezier for smoother curve
                val prevX = w * (index - 1) / (visiblePoints.size - 1)
                val prevY = h * (1f - visiblePoints[index - 1].coerceIn(0f, 1f))
                val cpX = (prevX + x) / 2f
                path.cubicTo(cpX, prevY, cpX, y, x, y)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthPx)
        )

        // Draw endpoint dot
        if (visiblePoints.isNotEmpty()) {
            val lastIndex = visiblePoints.size - 1
            val lastX = if (visiblePoints.size == 1) 0f
            else w * lastIndex / (visiblePoints.size - 1)
            val lastY = h * (1f - visiblePoints.last().coerceIn(0f, 1f))
            drawCircle(
                color = color,
                radius = 4f,
                center = Offset(lastX, lastY)
            )
        }
    }
}
