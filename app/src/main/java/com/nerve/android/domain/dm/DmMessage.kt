package com.nerve.android.domain.dm

data class DmMessage(
    val id: String,
    val role: DmRole,
    val content: String,
    val timestamp: Long,
    val nodeId: String,
    val nodeName: String,
)

enum class DmRole {
    USER,
    ASSISTANT,
    SYSTEM,
}
