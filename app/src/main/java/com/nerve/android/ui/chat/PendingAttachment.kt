package com.nerve.android.ui.chat

data class PendingAttachment(
    val mimeType: String,
    val base64Data: String,
    val displayName: String? = null,
)
