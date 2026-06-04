package com.nerve.android.ui.chat

data class PendingAttachment(
    val mimeType: String,
    val base64Data: String? = null,
    val displayName: String? = null,
    val bytes: ByteArray? = null,
    val kind: Kind = Kind.IMAGE,
    val sizeBytes: Long = bytes?.size?.toLong() ?: 0L,
)

enum class Kind {
    IMAGE,
    FILE,
}
