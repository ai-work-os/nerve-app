package com.nerve.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NerveColorScheme = darkColorScheme()

@Composable
fun NerveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NerveColorScheme,
        content = content,
    )
}
