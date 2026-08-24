package com.domedav.mavjegy.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val Green = Color(0xFF006D3B)
private val GreenDark = Color(0xFF6CDBA0)
private val Teal = Color(0xFF00696B)
private val TealDark = Color(0xFF4CDADC)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9FF6C6),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1F2),
    onSecondaryContainer = Color(0xFF002021),
    tertiary = Color(0xFF456179),
    surface = Color(0xFFF7FBF4),
    background = Color(0xFFF7FBF4),
    surfaceContainerHighest = Color(0xFFDBE5DA)
)

private val DarkColors = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF00522C),
    onPrimaryContainer = Color(0xFF89F8B1),
    secondary = TealDark,
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F50),
    onSecondaryContainer = Color(0xFF70F5F5),
    tertiary = Color(0xFFACC1E8),
    surface = Color(0xFF101511),
    background = Color(0xFF101511),
    surfaceContainerHighest = Color(0xFF353B37)
)

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MavJegyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ExpressiveShapes,
        content = content
    )
}
