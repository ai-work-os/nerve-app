package com.nerve.android.ui.chat

internal object ChatInputState {
    fun isSendEnabled(
        text: String,
        hasAttachment: Boolean,
        canSend: Boolean,
        isSending: Boolean,
        isStreaming: Boolean = false,
    ): Boolean {
        if (!canSend || isSending || isStreaming) return false
        return text.isNotBlank() || hasAttachment
    }
}
