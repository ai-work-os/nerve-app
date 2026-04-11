package com.nerve.android.domain.dm

data class DmMessage(
    val id: String,
    val role: DmRole,
    val content: String,
    val timestamp: Long,
    val nodeId: String,
    val nodeName: String,
    val blocks: List<ContentBlock> = emptyList(),
)

sealed interface ContentBlock {
    data class Text(val text: String) : ContentBlock
    data class Thinking(val text: String, val completed: Boolean = false) : ContentBlock
    data class ToolCall(val toolId: String, val toolName: String, val input: String) : ContentBlock
}

enum class DmRole {
    USER,
    ASSISTANT,
    SYSTEM,
}
