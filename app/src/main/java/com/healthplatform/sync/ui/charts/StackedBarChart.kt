package com.healthplatform.sync.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.healthplatform.sync.ui.theme.ApexOnSurfaceVariant
import com.healthplatform.sync.ui.theme.ApexOutline
import com.healthplatform.sync.ui.theme.ApexPrimary
import com.healthplatform.sync.ui.theme.ApexSecondary
import com.healthplatform.sync.ui.theme.ApexSurfaceVariant

data class SleepBar(
    val date: String,
    val deepMin: Int,
    val remMin: Int,
    val lightMin: Int,
    val awakeMin: Int
)

private val ColorDeep = ApexPrimary        // teal
private val ColorRem = ApexSecondary       // blue
private val ColorLight = ApexSurfaceVariant // dark surface variant
private val ColorAwake = ApexOutline       // outline grey

/**
 * Stacked bar chart for sleep stages per night.
 * Each bar represents one night, stacked: awake (bottom) | light | rem | deep (top).
 */
@Composable
fun StackedBarChart(
    bars: List<SleepBar>,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(bars) {
        progress.snapTo(0f)
        if (bars.isNotEmpty()) {
            progress.animateTo(1f, animationSpec = tween(durationMillis = 800))
        }
    }

    if (bars.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "No sleep data available",
                style = MaterialTheme.typography.bodyMedium,
                color = ApexOnSurfaceVariant
            )
        }
        return
    }

    val paddingLeft = 8f
    val paddingRight = 8f
    val paddingTop = 8f
    val paddingBottom = 32f

    Canvas(modifier = modifier.fillMaxSize()) {
        val chartWidth = size.width - paddingLeft - paddingRight
        val chartHeight = size.height - paddingTop - paddingBottom

        val maxTotal = bars.maxOf { it.deepMin + it.remMin + it.lightMin + it.awakeMin }
            .coerceAtLeast(1)

        val barWidth = (chartWidth / bars.size) * 0.7f
        val barSpacing = chartWidth / bars.size

        bars.forEachIndexed { index, bar ->
            val totalMin = bar.deepMin + bar.remMin + bar.lightMin + bar.awakeMin
            val barX = paddingLeft + index * barSpacing + (barSpacing - barWidth) / 2f

            // Segments from bottom up: awake, light, rem, deep
            val segments = listOf(
                bar.awakeMin to ColorAwake,
                bar.lightMin to ColorLight,
                bar.remMin to ColorRem,
                bar.deepMin to ColorDeep
            )

            var currentBottom = size.height - paddingBottom
            for ((minutes, color) in segments) {
                val segHeight = (chartHeight * minutes / maxTotal) * progress.value
                val top = currentBottom - segHeight
                if (segHeight > 0.5f) {
                    drawRect(
                        color = color,
                        topLeft = Offset(barX, top),
                        size = Size(barWidth, segHeight)
                    )
                }
                currentBottom = top
            }

            // Date label
            val label = bar.date.takeLast(5).let {
                // Format "MM-DD" → "M/D"
                try {
                    val parts = it.split("-")
                    if (parts.size >= 2) "${parts[0].trimStart('0')}/${parts[1].trimStart('0')}"
                    else it
                } catch (e: Exception) { it }
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    label,
                    barX + barWidth / 2f,
                    size.height - 8f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(160, 139, 148, 158)
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}
