package com.example.ballsortgame.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFFC5B1E8),
    onPrimary = Color(0xFF281C3A),
    secondary = Color(0xFFA0D2BE),
    onSecondary = Color(0xFF112D23),
    secondaryContainer = Color(0xFF30253F),
    onSecondaryContainer = Color(0xFFEAE0F4),
    background = Color(0xFF100D18),
    onBackground = Color(0xFFECE5F2),
    surface = Color(0xFF1B1525),
    onSurface = Color(0xFFECE5F2),
    surfaceVariant = Color(0xFF292132),
    onSurfaceVariant = Color(0xFFB8AEC5),
    outline = Color(0xFF665B75),
)

@Composable
fun BallSortGameTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, typography = Typography, content = content)
}
