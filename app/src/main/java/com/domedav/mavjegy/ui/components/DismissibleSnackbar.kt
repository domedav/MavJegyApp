package com.domedav.mavjegy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Eltűntethető snackbar: jobbra-balra húzással VAGY lehúzással eltűnik.
 * Felfelé NEM húzható (offsetY >= 0). 5 mp után automatikusan eltűnik.
 * Szín: error (hiba) vagy primaryContainer (info/siker).
 */
@Composable
fun DismissibleSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = true
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var visible by remember(message) { mutableStateOf(true) }

    // 5 mp után automatikus eltűnés
    LaunchedEffect(Unit) {
        delay(5000)
        if (visible) {
            visible = false
            onDismiss()
        }
    }

    if (!visible) return

    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.inverseSurface
    val contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.inverseOnSurface
    val icon = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info

    val dragStateX = rememberDraggableState { delta -> offsetX += delta }
    // Felfelé nem mehet: offsetY csak nem-negatív lehet
    val dragStateY = rememberDraggableState { delta -> offsetY = (offsetY + delta).coerceAtLeast(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer {
                    alpha = 1f - (abs(offsetX) / 600f).coerceIn(0f, 0.9f)
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragStateX,
                    onDragStopped = {
                        if (abs(offsetX) > 220f) {
                            visible = false
                            onDismiss()
                        } else {
                            offsetX = 0f
                        }
                    }
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = dragStateY,
                    onDragStopped = {
                        if (offsetY > 140f) {
                            visible = false
                            onDismiss()
                        } else {
                            offsetY = 0f
                        }
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
