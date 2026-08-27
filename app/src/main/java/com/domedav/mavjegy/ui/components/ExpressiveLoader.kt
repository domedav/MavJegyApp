package com.domedav.mavjegy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ExpressiveLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 56.dp,
    strokeWidth: Dp = 5.5.dp
) {
    val twoPi = (2.0 * PI).toFloat()
    val infinite = rememberInfiniteTransition(label = "wavy")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "phase"
    )
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "progress"
    )
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val radius = this.size.minDimension / 2f - stroke * 2f
        val amp = stroke * 1.15f
        val sweepMax = twoPi * 0.72f
        val start = (progress * twoPi * 2f) % twoPi
        val path = Path()
        var first = true
        var angle = start
        while (angle <= start + sweepMax) {
            val wave = sin(angle * 3f + phase)
            val rr = radius + amp * wave
            val x = center.x + rr * cos(angle)
            val y = center.y + rr * sin(angle)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            angle += 0.055f
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
