package com.nerve.android.domain.dm

sealed interface DmMappedEvent {
    data class UserMessage(
        val nodeId: String,
        val nodeName: String,
        val content: String,
        val timestamp: Long,
        val messageId: String,
    ) : DmMappedEvent

    data class AgentMessageStart(
        val nodeId: String,
        val nodeName: String,
        val timestamp: Long,
        val messageId: String,
    ) : DmMappedEvent

    data class AgentMessageChunk(
        val nodeId: String,
        val text: String,
        val timestamp: Long,
    ) : DmMappedEvent

    data class AgentMessageEnd(
        val nodeId: String,
        val nodeName: String,
        val timestamp: Long,
        val fallbackText: String?,
    ) : DmMappedEvent

    data class NodeIdle(
        val nodeId: String,
        val timestamp: Long,
    ) : DmMappedEvent

    data class AgentThoughtChunk(
        val nodeId: String,
        val text: String,
        val timestamp: Long,
    ) : DmMappedEvent

    data class AgentThoughtEnd(
        val nodeId: String,
        val timestamp: Long,
    ) : DmMappedEvent

    data class ToolCall(
        val nodeId: String,
        val toolId: String,
        val toolName: String,
        val input: String,
        val timestamp: Long,
    ) : DmMappedEvent

    data class ToolCallUpdate(
        val nodeId: String,
        val toolId: String,
        val status: String,
        val output: String?,
        val timestamp: Long,
    ) : DmMappedEvent

    data object Ignore : DmMappedEvent
}
