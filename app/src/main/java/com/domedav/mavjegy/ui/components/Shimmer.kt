package com.domedav.mavjegy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

fun Modifier.shimmerPlaceholder(): Modifier = composed {
    val t by rememberInfiniteTransition("s").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1000, easing = LinearEasing)),
        "a"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    background(
        Brush.linearGradient(
            colors = listOf(base, base.copy(alpha = 0.4f), base),
            start = Offset(-400f * t, 0f),
            end = Offset(400f * (1 + t), 0f)
        )
    )
}
