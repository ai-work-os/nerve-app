package com.nerve.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark theme
val Background = Color(0xFF0F172A)
val Surface = Color(0xFF1E293B)
val SurfaceVariant = Color(0xFF334155)
val OnSurface = Color(0xFFF8FAFC)
val OnSurfaceVariant = Color(0xFF94A3B8)
val Outline = Color(0xFF475569)
val Primary = Color(0xFF818CF8)

// Light theme
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFF1F5F9)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF64748B)
val LightOutline = Color(0xFFCBD5E1)
val LightPrimary = Color(0xFF4F46E5)

// Status colors
val StatusIdle = Color(0xFF10B981)
val StatusBusy = Color(0xFF8B5CF6)
val StatusError = Color(0xFFF43F5E)
val StatusStopped = Color(0xFF64748B)

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
