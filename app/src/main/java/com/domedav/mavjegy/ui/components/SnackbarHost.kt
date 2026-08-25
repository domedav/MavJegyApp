package com.domedav.mavjegy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

data class SnackbarModel(val message: String, val isError: Boolean)

class SnackbarState {
    var current by mutableStateOf<SnackbarModel?>(null)
    fun show(message: String, isError: Boolean = true) {
        current = SnackbarModel(message, isError)
    }
}

val LocalSnackbar = compositionLocalOf<SnackbarState> {
    error("SnackbarHost nincs bekötve (LocalSnackbar)")
}

/**
 * Globális snackbar overlay: a widget-tree-n kívül, a képernyő tetején jelenik meg
 * (Popup), így minden más elem fölött úszik és takarhat is. Egyidejűleg egy snackbar látszik.
 */
@Composable
fun SnackbarHost() {
    val state = LocalSnackbar.current
    val current = state.current
    if (current != null) {
        val bottomPx = with(LocalDensity.current) { 16.dp.toPx() }.roundToInt()
        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, -bottomPx),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            DismissibleSnackbar(
                message = current.message,
                isError = current.isError,
                onDismiss = { state.current = null }
            )
        }
    }
}
