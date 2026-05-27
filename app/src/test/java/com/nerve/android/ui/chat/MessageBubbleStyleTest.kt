package com.nerve.android.ui.chat

import androidx.compose.ui.graphics.toArgb
import com.nerve.android.ui.theme.WhiteSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageBubbleStyleTest {
    @Test
    fun `assistant markdown text view background matches bubble background`() {
        assertEquals(
            WhiteSurface.toArgb(),
            markdownTextBackgroundColor(isUser = false).toArgb(),
        )
    }
}
