package com.nerve.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Creamy Latte Palette
val CreamBackground = Color(0xFFFFFCF5)
val WhiteSurface = Color(0xFFFFFFFF)
val StoneMain = Color(0xFF292524)
val StoneMuted = Color(0xFF78716C)
val AmberPrimary = Color(0xFFF59E0B)
val AmberSecondary = Color(0xFFFBBF24)
val RoseAccent = Color(0xFFF43F5E)

// Dark theme (Deep Stone)
val DeepStoneBackground = Color(0xFF0C0A09)
val DarkSurface = Color(0xFF1C1917)

// Status colors (Warm optimized)
val StatusIdle = Color(0xFF10B981)
val StatusBusy = AmberPrimary
val StatusError = RoseAccent
val StatusStopped = StoneMuted

private val NerveDarkColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.Black,
    background = DeepStoneBackground,
    onBackground = CreamBackground,
    surface = DarkSurface,
    onSurface = CreamBackground,
    surfaceVariant = Color(0xFF292524),
    onSurfaceVariant = StoneMuted,
    outline = Color(0xFF44403C),
)

private val NerveLightColorScheme = lightColorScheme(
    primary = AmberPrimary,
    onPrimary = Color.White,
    background = CreamBackground,
    onBackground = StoneMain,
    surface = WhiteSurface,
    onSurface = StoneMain,
    surfaceVariant = Color(0xFFF5F5F4),
    onSurfaceVariant = StoneMuted,
    outline = Color(0xFFE7E5E4),
)

fun statusColor(status: String): Color = when (status) {
    "idle" -> StatusIdle
    "busy" -> StatusBusy
    "error" -> StatusError
    "stopped" -> StatusStopped
    else -> StoneMuted
}

@Composable
fun NerveTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) NerveDarkColorScheme else NerveLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
