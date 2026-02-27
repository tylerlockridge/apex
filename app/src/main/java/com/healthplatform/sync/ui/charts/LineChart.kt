package com.healthplatform.sync.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.healthplatform.sync.ui.theme.ApexOnSurface
import com.healthplatform.sync.ui.theme.ApexOnSurfaceVariant
import com.healthplatform.sync.ui.theme.ApexOutline
import com.healthplatform.sync.ui.theme.ApexPrimary
import com.healthplatform.sync.ui.theme.ApexSurfaceVariant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Full interactive line chart for Trends screen.
 * Supports animated reveal, tap-to-tooltip, grid lines, and optional baseline bands.
 *
 * @param dataPoints List of (timestamp millis, value) pairs, sorted ascending
 * @param lineColor Color of the primary line
 * @param yLabel Unit label for Y axis
 * @param showBaselines Whether to render colored horizontal band regions
 * @param baselines List of (min, max, color) for band regions
 */
@Composable
fun LineChart(
    dataPoints: List<Pair<Long, Float>>,
    lineColor: Color = ApexPrimary,
    modifier: Modifier = Modifier,
    yLabel: String = "",
    showBaselines: Boolean = false,
    baselines: List<Triple<Float, Float, Color>> = emptyList()
) {
    val progress = remember { Animatable(0f) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(dataPoints) {
        progress.snapTo(0f)
        if (dataPoints.isNotEmpty()) {
            progress.animateTo(1f, animationSpec = tween(durationMillis = 900))
        }
    }

    if (dataPoints.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No data available",
                style = MaterialTheme.typography.bodyMedium,
                color = ApexOnSurfaceVariant
            )
        }
        return
    }

    val paddingLeft = 52f
    val paddingRight = 16f
    val paddingTop = 16f
    val paddingBottom = 40f

    val minY = dataPoints.minOf { it.second }
    val maxY = dataPoints.maxOf { it.second }
    val yRange = (maxY - minY).coerceAtLeast(1f)
    val minX = dataPoints.minOf { it.first }
    val maxX = dataPoints.maxOf { it.first }
    val xRange = (maxX - minX).coerceAtLeast(1L)

    val dateFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
    val tooltipDateFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dataPoints) {
                    detectTapGestures { tapOffset ->
                        val chartWidth = size.width - paddingLeft - paddingRight
                        val relativeX = (tapOffset.x - paddingLeft).coerceIn(0f, chartWidth)
                        val fraction = relativeX / chartWidth
                        val tappedIndex = (fraction * (dataPoints.size - 1)).toInt()
                            .coerceIn(0, dataPoints.size - 1)
                        selectedIndex = if (selectedIndex == tappedIndex) null else tappedIndex
                    }
                }
        ) {
            val chartLeft = paddingLeft
            val chartRight = size.width - paddingRight
            val chartTop = paddingTop
            val chartBottom = size.height - paddingBottom
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            fun xForTimestamp(ts: Long): Float =
                chartLeft + chartWidth * ((ts - minX).toFloat() / xRange)

            fun yForValue(v: Float): Float =
                chartBottom - chartHeight * ((v - minY) / yRange)

            // Draw baseline bands
            if (showBaselines && baselines.isNotEmpty()) {
                for ((bMin, bMax, bColor) in baselines) {
                    val bandTop = yForValue(bMax).coerceAtLeast(chartTop)
                    val bandBottom = yForValue(bMin).coerceAtMost(chartBottom)
                    if (bandBottom > bandTop) {
                        drawRect(
                            color = bColor.copy(alpha = 0.12f),
                            topLeft = Offset(chartLeft, bandTop),
                            size = Size(chartWidth, bandBottom - bandTop)
                        )
                    }
                }
            }

            // Horizontal grid lines (5 lines)
            val gridCount = 4
            for (i in 0..gridCount) {
                val y = chartTop + chartHeight * i / gridCount
                drawLine(
                    color = ApexOutline,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1f
                )
                // Y axis label
                val labelValue = maxY - yRange * i / gridCount
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(labelValue),
                    chartLeft - 8f,
                    y + 4f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(180, 139, 148, 158)
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                )
            }

            // X axis date labels (up to 5)
            val labelCount = minOf(dataPoints.size, 5)
            if (labelCount > 1) {
                for (i in 0 until labelCount) {
                    val dataIdx = i * (dataPoints.size - 1) / (labelCount - 1)
                    val ts = dataPoints[dataIdx].first
                    val x = xForTimestamp(ts)
                    val label = dateFormatter.format(Instant.ofEpochMilli(ts))
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        x,
                        size.height - 8f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(180, 139, 148, 158)
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }

            // Determine visible point count from animation
            val visibleCount = (dataPoints.size * progress.value).toInt().coerceAtLeast(2)
                .coerceAtMost(dataPoints.size)
            val visiblePoints = dataPoints.take(visibleCount)

            // Draw line
            if (visiblePoints.size >= 2) {
                val path = Path()
                visiblePoints.forEachIndexed { i, (ts, value) ->
                    val x = xForTimestamp(ts)
                    val y = yForValue(value)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.5f)
                )
            }

            // Draw dots for each visible point
            for ((ts, value) in visiblePoints) {
                drawCircle(
                    color = lineColor,
                    radius = 4f,
                    center = Offset(xForTimestamp(ts), yForValue(value))
                )
            }

            // Selected point + tooltip
            selectedIndex?.let { idx ->
                if (idx < dataPoints.size) {
                    val (ts, value) = dataPoints[idx]
                    val x = xForTimestamp(ts)
                    val y = yForValue(value)

                    // Vertical tap line
                    drawLine(
                        color = lineColor.copy(alpha = 0.4f),
                        start = Offset(x, chartTop),
                        end = Offset(x, chartBottom),
                        strokeWidth = 1f
                    )

                    // Highlight dot
                    drawCircle(
                        color = lineColor,
                        radius = 7f,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f,
                        center = Offset(x, y)
                    )

                    // Tooltip box
                    val tooltipText = "%.1f $yLabel".format(value)
                    val dateText = tooltipDateFormatter.format(Instant.ofEpochMilli(ts))
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                    }
                    val textWidth = maxOf(paint.measureText(tooltipText), paint.measureText(dateText))
                    val tooltipW = textWidth + 24f
                    val tooltipH = 64f
                    var tooltipX = x - tooltipW / 2f
                    tooltipX = tooltipX.coerceIn(chartLeft, chartRight - tooltipW)
                    val tooltipY = (y - tooltipH - 12f).coerceAtLeast(chartTop)

                    drawRoundRect(
                        color = ApexSurfaceVariant,
                        topLeft = Offset(tooltipX, tooltipY),
                        size = Size(tooltipW, tooltipH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        tooltipText,
                        tooltipX + tooltipW / 2f,
                        tooltipY + 24f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        dateText,
                        tooltipX + tooltipW / 2f,
                        tooltipY + 52f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(200, 139, 148, 158)
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
        }
    }
}
