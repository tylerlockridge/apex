package com.healthplatform.sync.ui.util

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shimmer loading effect as a Modifier extension.
 *
 * Usage:
 *   Box(
 *       modifier = Modifier
 *           .fillMaxWidth()
 *           .height(20.dp)
 *           .clip(RoundedCornerShape(4.dp))
 *           .shimmer()
 *   )
 */
fun Modifier.shimmer(
    shimmerBase: Color = Color(0xFF1B2640),
    shimmerHighlight: Color = Color(0xFF2A3A58)
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(shimmerBase, shimmerHighlight, shimmerBase),
            start = Offset(translateAnim - 400f, 0f),
            end = Offset(translateAnim, 0f)
        )
    )
}
