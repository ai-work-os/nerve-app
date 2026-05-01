package com.nerve.android.domain.dm

data class DmMessage(
    val id: String,
    val role: DmRole,
    val content: String,
    val timestamp: Long,
    val nodeId: String,
    val nodeName: String,
    val blocks: List<ContentBlock> = emptyList(),
    val action: DmAction? = null,
) {
    /** Summary mode: only Text blocks, filtering Thinking/ToolCall. */
    val textContent: String
        get() {
            if (blocks.isEmpty()) return content
            return blocks.filterIsInstance<ContentBlock.Text>()
                .joinToString("") { it.text }
                .trim()
        }
}

sealed interface DmAction {
    val serverId: String
    val nodeId: String
    val nodeName: String

    data class OpenDm(
        override val serverId: String,
        override val nodeId: String,
        override val nodeName: String,
    ) : DmAction
}

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
