package com.nerve.android.domain.dm

import com.nerve.android.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private enum class FlushReason {
    USER_MESSAGE,
    NODE_IDLE,
}

class DmSessionManager {

    private val _messages = MutableStateFlow<List<DmMessage>>(emptyList())
    val messages: StateFlow<List<DmMessage>> = _messages.asStateFlow()

    private val _streamingMessage = MutableStateFlow<DmMessage?>(null)
    val streamingMessage: StateFlow<DmMessage?> = _streamingMessage.asStateFlow()

    // Not thread-safe — must be called from single coroutine
    private var state: DmStreamingState? = null
    private var batchMode = false
    private var pendingMessages: MutableList<DmMessage>? = null

    fun onEvent(event: DmMappedEvent) {
        when (event) {
            is DmMappedEvent.AgentMessageStart -> handleStart(event)
            is DmMappedEvent.AgentMessageChunk -> handleChunk(event)
            is DmMappedEvent.AgentThoughtChunk -> handleThoughtChunk(event)
            is DmMappedEvent.ToolCall -> handleToolCall(event)
            is DmMappedEvent.AgentMessageEnd -> handleEnd(event)
            is DmMappedEvent.UserMessage -> handleUserMessage(event)
            is DmMappedEvent.NodeIdle -> handleIdle(event)
            else -> Logger.warn(
                "DmSessionManager",
                "unhandled_event",
                mapOf("eventType" to event::class.simpleName, "reason" to "unsupported_event"),
            )
        }
    }

    fun beginBatch() {
        Logger.debug("DmSessionManager", "batch_begin")
        batchMode = true
        pendingMessages = mutableListOf()
    }

    fun endBatch() {
        val pending = pendingMessages ?: return
        Logger.debug("DmSessionManager", "batch_end", mapOf("count" to pending.size))
        batchMode = false
        pendingMessages = null
        _messages.value = _messages.value + pending
    }

    fun reset() {
        Logger.debug("DmSessionManager", "session_reset", mapOf("count" to _messages.value.size))
        state = null
        _messages.value = emptyList()
        _streamingMessage.value = null
    }

    /**
     * Replace the entire DM history with an authoritative snapshot from the server.
     * Used on subscribe (first time and on reconnect-resubscribe). Discards any
     * in-flight streaming state and resets the message list to the snapshot contents.
     * Safe to call in or out of a batch; pending batch messages are discarded.
     */
    fun replaceHistory(messages: List<DmMessage>) {
        Logger.debug("DmSessionManager", "history_replace", mapOf("count" to messages.size))
        state = null
        pendingMessages = null
        batchMode = false
        _streamingMessage.value = null
        _messages.value = messages
    }

    fun addSystemMessage(message: DmMessage) {
        Logger.debug(
            "DmSessionManager",
            "system_message_add",
            mapOf("messageId" to message.id, "hasAction" to (message.action != null)),
        )
        addMessage(message)
    }

    // --- internals ---

    private fun handleStart(event: DmMappedEvent.AgentMessageStart) {
        Logger.debug(
            "DmSessionManager",
            "stream_start",
            mapOf("messageId" to event.messageId, "nodeId" to event.nodeId),
        )
        state = DmStreamingState(
            messageId = event.messageId,
            nodeId = event.nodeId,
            nodeName = event.nodeName,
            startedAt = event.timestamp,
            lastEventAt = event.timestamp,
        )
    }

    private fun ensureState(nodeId: String, timestamp: Long): DmStreamingState {
        return state ?: DmStreamingState(
            messageId = "implicit:$nodeId:$timestamp",
            nodeId = nodeId,
            nodeName = "",
            startedAt = timestamp,
            lastEventAt = timestamp,
        ).also {
            Logger.debug("DmSessionManager", "stream_implicit_start", mapOf("nodeId" to nodeId))
            state = it
        }
    }

    private fun handleChunk(event: DmMappedEvent.AgentMessageChunk) {
        val s = ensureState(event.nodeId, event.timestamp)
        Logger.debug("DmSessionManager", "stream_chunk", mapOf("len" to event.text.length))
        s.text.append(event.text)
        s.lastEventAt = event.timestamp
        val last = s.blocks.lastOrNull()
        if (last is ContentBlock.Text) {
            s.blocks[s.blocks.lastIndex] = ContentBlock.Text(last.text + event.text)
        } else {
            s.blocks.add(ContentBlock.Text(event.text))
        }
        emitSnapshot(s)
    }

    private fun handleThoughtChunk(event: DmMappedEvent.AgentThoughtChunk) {
        val s = ensureState(event.nodeId, event.timestamp)
        s.lastEventAt = event.timestamp
        val last = s.blocks.lastOrNull()
        if (last is ContentBlock.Thinking && !last.completed) {
            s.blocks[s.blocks.lastIndex] = ContentBlock.Thinking(last.text + event.text)
        } else {
            s.blocks.add(ContentBlock.Thinking(event.text))
        }
        emitSnapshot(s)
    }

    private fun handleToolCall(event: DmMappedEvent.ToolCall) {
        val s = ensureState(event.nodeId, event.timestamp)
        s.lastEventAt = event.timestamp
        val last = s.blocks.lastOrNull()
        if (last is ContentBlock.Thinking && !last.completed) {
            s.blocks[s.blocks.lastIndex] = ContentBlock.Thinking(last.text, completed = true)
        }
        s.blocks.add(ContentBlock.ToolCall(event.toolId, event.toolName, event.input))
        emitSnapshot(s)
    }

    private fun handleEnd(event: DmMappedEvent.AgentMessageEnd) {
        val s = state
        if (s != null) {
            Logger.debug("DmSessionManager", "stream_end", mapOf("messageId" to s.messageId))
            state = null
            if (!batchMode) _streamingMessage.value = null
            s.lastEventAt = event.timestamp
            val content = s.text.toString().ifBlank { event.fallbackText.orEmpty() }.trim()
            val msg = buildAssistantMsg(s, content, event.timestamp) ?: return
            addMessage(msg)
        } else {
            val text = event.fallbackText?.trim().orEmpty()
            if (text.isBlank()) {
                Logger.warn(
                    "DmSessionManager",
                    "stream_end_without_active",
                    mapOf("reason" to "no_active_stream_no_fallback"),
                )
                return
            }
            Logger.debug("DmSessionManager", "stream_end", mapOf("nodeId" to event.nodeId))
            val msg = DmMessage(
                id = buildAssistantMessageId(event.nodeId, event.timestamp, text),
                role = DmRole.ASSISTANT,
                content = text,
                timestamp = event.timestamp,
                nodeId = event.nodeId,
                nodeName = event.nodeName,
                blocks = listOf(ContentBlock.Text(text)),
            )
            addMessage(msg)
        }
    }

    private fun handleUserMessage(event: DmMappedEvent.UserMessage) {
        flushStreaming(FlushReason.USER_MESSAGE, event.timestamp)
        val userMsg = DmMessage(
            id = event.messageId,
            role = DmRole.USER,
            content = event.content,
            timestamp = event.timestamp,
            nodeId = event.nodeId,
            nodeName = event.nodeName,
        )
        addMessage(userMsg)
    }

    private fun handleIdle(event: DmMappedEvent.NodeIdle) {
        flushStreaming(FlushReason.NODE_IDLE, event.timestamp)
    }

    private fun flushStreaming(reason: FlushReason, timestamp: Long) {
        val s = state ?: return
        Logger.debug("DmSessionManager", "stream_flush", mapOf("reason" to reason))
        state = null
        if (!batchMode) _streamingMessage.value = null
        val content = s.text.toString().trim()
        val messageTimestamp = when (reason) {
            FlushReason.USER_MESSAGE -> s.lastEventAt
            else -> timestamp
        }
        val msg = buildAssistantMsg(s, content, messageTimestamp) ?: return
        addMessage(msg)
    }

    private fun buildAssistantMsg(s: DmStreamingState, content: String, timestamp: Long): DmMessage? {
        val finalContent = content.ifBlank {
            s.blocks.joinToString("\n") { block ->
                when (block) {
                    is ContentBlock.Text -> block.text
                    is ContentBlock.Thinking -> "[thinking] ${block.text}"
                    is ContentBlock.ToolCall -> "[tool: ${block.toolName}]"
                }
            }.trim()
        }
        if (finalContent.isBlank()) return null
        return DmMessage(
            id = buildAssistantMessageId(s.nodeId, timestamp, finalContent),
            role = DmRole.ASSISTANT,
            content = finalContent,
            timestamp = timestamp,
            nodeId = s.nodeId,
            nodeName = s.nodeName,
            blocks = s.blocks.toList(),
        )
    }

    private fun addMessage(msg: DmMessage) {
        if (batchMode) {
            pendingMessages?.add(msg)
        } else {
            _messages.value = _messages.value + msg
        }
    }

    private fun emitSnapshot(s: DmStreamingState) {
        if (batchMode) return
        val content = s.text.toString().ifBlank {
            s.blocks.joinToString("\n") { block ->
                when (block) {
                    is ContentBlock.Text -> block.text
                    is ContentBlock.Thinking -> "[thinking] ${block.text}"
                    is ContentBlock.ToolCall -> "[tool: ${block.toolName}]"
                }
            }.trim()
        }
        _streamingMessage.value = DmMessage(
            id = s.messageId,
            role = DmRole.ASSISTANT,
            content = content,
            timestamp = s.lastEventAt,
            nodeId = s.nodeId,
            nodeName = s.nodeName,
            blocks = s.blocks.toList(),
        )
    }
}
