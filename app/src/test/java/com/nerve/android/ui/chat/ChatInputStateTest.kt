package com.nerve.android.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatInputStateTest {
    @Test
    fun `text only enables send`() {
        assertTrue(
            ChatInputState.isSendEnabled(
                text = "hello",
                hasAttachment = false,
                canSend = true,
                isSending = false,
            ),
        )
    }

    @Test
    fun `attachment only enables send when text blank`() {
        assertTrue(
            ChatInputState.isSendEnabled(
                text = "",
                hasAttachment = true,
                canSend = true,
                isSending = false,
            ),
        )
    }

    @Test
    fun `text and attachment together enables send`() {
        assertTrue(
            ChatInputState.isSendEnabled(
                text = "see this",
                hasAttachment = true,
                canSend = true,
                isSending = false,
            ),
        )
    }

    @Test
    fun `blank text and no attachment disables send`() {
        assertFalse(
            ChatInputState.isSendEnabled(
                text = "   ",
                hasAttachment = false,
                canSend = true,
                isSending = false,
            ),
        )
    }

    @Test
    fun `canSend false disables regardless`() {
        assertFalse(
            ChatInputState.isSendEnabled(
                text = "hi",
                hasAttachment = true,
                canSend = false,
                isSending = false,
            ),
        )
    }

    @Test
    fun `isSending true disables regardless`() {
        assertFalse(
            ChatInputState.isSendEnabled(
                text = "hi",
                hasAttachment = true,
                canSend = true,
                isSending = true,
                isStreaming = false,
            ),
        )
    }

    @Test
    fun `isStreaming true disables regardless`() {
        assertFalse(
            ChatInputState.isSendEnabled(
                text = "hi",
                hasAttachment = true,
                canSend = true,
                isSending = false,
                isStreaming = true,
            ),
        )
    }
}
