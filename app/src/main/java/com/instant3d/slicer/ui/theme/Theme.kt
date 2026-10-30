package com.instant3d.slicer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EngineeringDark = darkColorScheme(
    primary = Color(0xFF73E0C1),
    onPrimary = Color(0xFF00382E),
    secondary = Color(0xFF9BD0C0),
    background = Color(0xFF101413),
    surface = Color(0xFF191F1D),
    surfaceVariant = Color(0xFF202927),
    onBackground = Color(0xFFE1E9E5),
    onSurface = Color(0xFFE1E9E5),
    onSurfaceVariant = Color(0xFFA2B0AA),
)

@Composable
fun Instant3DSlicerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EngineeringDark, content = content)
}
