package com.nerve.android.ui.chat

internal object ChatInputState {
    fun isSendEnabled(
        text: String,
        hasAttachment: Boolean,
        canSend: Boolean,
        isSending: Boolean,
    ): Boolean {
        if (!canSend || isSending) return false
        return text.isNotBlank() || hasAttachment
    }
}
