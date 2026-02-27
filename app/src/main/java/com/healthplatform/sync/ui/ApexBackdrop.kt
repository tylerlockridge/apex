package com.healthplatform.sync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Draws the Apex mountain-A lettermark as a ghost watermark.
 *
 * Design: the icon chevron (M34,76 L54,30 L74,76) + crossbar (M42,58 L66,58)
 * from the adaptive icon, scaled to ~58% of the shorter screen dimension,
 * centered, at 5% white opacity against the dark navy background.
 *
 * Usage — apply as the first item inside any full-screen Box:
 *   Box(modifier = Modifier.fillMaxSize().background(ApexBackground)) {
 *       ApexWatermarkCanvas(modifier = Modifier.fillMaxSize())
 *       content()
 *   }
 */
@Composable
fun ApexWatermarkCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawApexWatermark()
    }
}

private fun DrawScope.drawApexWatermark() {
    // Scale the icon to ~58% of the shorter screen dimension
    val iconSize = minOf(size.width, size.height) * 0.58f

    // Icon viewport: bounding box of M34,76 L54,30 L74,76
    // x: 34..74 → width 40, y: 30..76 → height 46
    val viewW = 40f
    val viewH = 46f
    val scale = iconSize / maxOf(viewW, viewH)

    // Center the mark on screen, slightly above center for visual balance
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Offset so that the center of the viewport (x=54, y=53) lands at (cx, cy)
    val ox = cx - 54f * scale
    val oy = cy - 53f * scale

    val strokeColor = Color.White.copy(alpha = 0.05f)

    val chevronStroke = Stroke(
        width = iconSize * 0.065f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val crossbarStroke = Stroke(
        width = iconSize * 0.042f,
        cap = StrokeCap.Round
    )

    // Chevron: M34,76 L54,30 L74,76
    val chevron = Path().apply {
        moveTo(ox + 34f * scale, oy + 76f * scale)
        lineTo(ox + 54f * scale, oy + 30f * scale)
        lineTo(ox + 74f * scale, oy + 76f * scale)
    }
    drawPath(chevron, color = strokeColor, style = chevronStroke)

    // Crossbar: M42,58 L66,58
    val crossbar = Path().apply {
        moveTo(ox + 42f * scale, oy + 58f * scale)
        lineTo(ox + 66f * scale, oy + 58f * scale)
    }
    drawPath(crossbar, color = strokeColor, style = crossbarStroke)
}
