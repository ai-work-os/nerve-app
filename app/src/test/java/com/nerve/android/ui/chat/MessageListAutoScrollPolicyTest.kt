package com.nerve.android.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageListAutoScrollPolicyTest {
    @Test
    fun `streaming chunks follow only while viewer remains near the bottom`() {
        assertTrue(
            shouldAutoScrollMessageList(
                totalItems = 20,
                lastVisibleIndex = 19,
                isStreaming = true,
                itemCountChanged = false,
            ),
        )

        assertFalse(
            shouldAutoScrollMessageList(
                totalItems = 20,
                lastVisibleIndex = 12,
                isStreaming = true,
                itemCountChanged = false,
            ),
        )
    }

    @Test
    fun `new message follows only from the bottom window`() {
        assertTrue(
            shouldAutoScrollMessageList(
                totalItems = 20,
                lastVisibleIndex = 18,
                isStreaming = false,
                itemCountChanged = true,
            ),
        )

        assertFalse(
            shouldAutoScrollMessageList(
                totalItems = 20,
                lastVisibleIndex = 10,
                isStreaming = false,
                itemCountChanged = true,
            ),
        )
    }

    @Test
    fun `empty layout can auto scroll for first content`() {
        assertTrue(
            shouldAutoScrollMessageList(
                totalItems = 0,
                lastVisibleIndex = null,
                isStreaming = true,
                itemCountChanged = false,
            ),
        )
    }

    @Test
    fun `unchanged idle list does not request another scroll`() {
        assertFalse(
            shouldAutoScrollMessageList(
                totalItems = 20,
                lastVisibleIndex = 19,
                isStreaming = false,
                itemCountChanged = false,
            ),
        )
    }
}
