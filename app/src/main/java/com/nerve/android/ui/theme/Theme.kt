package com.nerve.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme
val Background = Color(0xFF0F1117)
val Surface = Color(0xFF1A1D27)
val SurfaceVariant = Color(0xFF22252F)
val OnSurface = Color(0xFFE4E4E7)
val OnSurfaceVariant = Color(0xFFA1A1AA)
val Outline = Color(0xFF2A2D3A)
val Primary = Color(0xFF3B82F6)

// Light theme
val LightBackground = Color(0xFFFAF8F5)
val LightSurface = Color(0xFFF5F0EB)
val LightSurfaceVariant = Color(0xFFEDE7E0)
val LightOnSurface = Color(0xFF3D3832)
val LightOnSurfaceVariant = Color(0xFF7A736B)
val LightOutline = Color(0xFFD9D2CA)
val LightPrimary = Color(0xFF2B8A7E)

// Status colors
val StatusIdle = Color(0xFF10B981)
val StatusBusy = Color(0xFFF59E0B)
val StatusError = Color(0xFFEF4444)
val StatusStopped = Color(0xFF6B7280)

fun statusColor(status: String): Color = when (status) {
    "idle" -> StatusIdle
    "busy" -> StatusBusy
    "error" -> StatusError
    "stopped" -> StatusStopped
    else -> OnSurfaceVariant
}

private val NerveDarkColorScheme = darkColorScheme(
    primary = Primary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
)

private val NerveLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
)

@Composable
fun NerveTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) NerveDarkColorScheme else NerveLightColorScheme,
        content = content,
    )
}
