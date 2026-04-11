package com.nerve.android.domain.dm

import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap

interface StreamingAccumulator {
    fun onStart(key: DmKey, nodeId: String, nodeName: String, messageId: String, timestamp: Long)
    fun onChunk(key: DmKey, text: String, timestamp: Long)
    fun onThoughtChunk(key: DmKey, text: String, timestamp: Long)
    fun onToolCall(key: DmKey, toolId: String, toolName: String, input: String, timestamp: Long)
    fun currentBlocks(key: DmKey): List<ContentBlock>
    fun onEnd(key: DmKey, fallbackText: String? = null, timestamp: Long): DmMessage?
    fun flush(key: DmKey, timestamp: Long, reason: FlushReason): DmMessage?
}

enum class FlushReason(val wireValue: String) {
    USER_MESSAGE("user_message"),
    NODE_IDLE("node_idle"),
    FALLBACK("fallback"),
}

class InMemoryStreamingAccumulator : StreamingAccumulator {
    private val activeStreams = ConcurrentHashMap<DmKey, DmStreamingState>()

    override fun onStart(key: DmKey, nodeId: String, nodeName: String, messageId: String, timestamp: Long) {
        activeStreams[key] = DmStreamingState(messageId, nodeId, nodeName, timestamp, timestamp)
        Logger.d("StreamingAccumulator", "stream start key=${key.value} msgId=$messageId")
    }

    private fun ensureState(key: DmKey, timestamp: Long): DmStreamingState {
        return activeStreams.getOrPut(key) {
            Logger.d("StreamingAccumulator", "stream implicit-start key=${key.value}")
            DmStreamingState("implicit:${key.value}:$timestamp", key.value, "", timestamp, timestamp)
        }
    }

    override fun onChunk(key: DmKey, text: String, timestamp: Long) {
        val state = ensureState(key, timestamp)
        state.text.append(text)
        state.lastEventAt = timestamp
        val last = state.blocks.lastOrNull()
        if (last is ContentBlock.Text) {
            state.blocks[state.blocks.lastIndex] = ContentBlock.Text(last.text + text)
        } else {
            state.blocks.add(ContentBlock.Text(text))
        }
        Logger.d("StreamingAccumulator", "stream chunk key=${key.value} len=${text.length} ts=$timestamp")
    }

    override fun onThoughtChunk(key: DmKey, text: String, timestamp: Long) {
        val state = ensureState(key, timestamp)
        state.lastEventAt = timestamp
        val last = state.blocks.lastOrNull()
        if (last is ContentBlock.Thinking && !last.completed) {
            state.blocks[state.blocks.lastIndex] = ContentBlock.Thinking(last.text + text)
        } else {
            state.blocks.add(ContentBlock.Thinking(text))
        }
    }

    override fun onToolCall(key: DmKey, toolId: String, toolName: String, input: String, timestamp: Long) {
        val state = ensureState(key, timestamp)
        state.lastEventAt = timestamp
        val last = state.blocks.lastOrNull()
        if (last is ContentBlock.Thinking && !last.completed) {
            state.blocks[state.blocks.lastIndex] = ContentBlock.Thinking(last.text, completed = true)
        }
        state.blocks.add(ContentBlock.ToolCall(toolId, toolName, input))
    }

    override fun currentBlocks(key: DmKey): List<ContentBlock> {
        return activeStreams[key]?.blocks?.toList() ?: emptyList()
    }

    override fun onEnd(key: DmKey, fallbackText: String?, timestamp: Long): DmMessage? {
        val state = activeStreams.remove(key) ?: return null
        state.lastEventAt = timestamp
        val content = state.text.toString().ifBlank { fallbackText.orEmpty() }.trim()
        Logger.d("StreamingAccumulator", "stream end key=${key.value} msgId=${state.messageId} ts=$timestamp")
        return buildAssistantMessage(state, content, timestamp)
    }

    override fun flush(key: DmKey, timestamp: Long, reason: FlushReason): DmMessage? {
        val state = activeStreams.remove(key) ?: return null
        Logger.d("StreamingAccumulator", "stream flush key=${key.value} reason=${reason.wireValue}")
        val content = state.text.toString().trim()
        val messageTimestamp = when (reason) {
            FlushReason.USER_MESSAGE -> state.lastEventAt
            else -> timestamp
        }
        return buildAssistantMessage(state, content, messageTimestamp)
    }

    private fun buildAssistantMessage(state: DmStreamingState, content: String, timestamp: Long): DmMessage? {
        val finalContent = content.ifBlank {
            state.blocks.joinToString("\n") { block ->
                when (block) {
                    is ContentBlock.Text -> block.text
                    is ContentBlock.Thinking -> "[thinking] ${block.text}"
                    is ContentBlock.ToolCall -> "[tool: ${block.toolName}]"
                }
            }.trim()
        }
        if (finalContent.isBlank()) return null
        return DmMessage(
            id = buildAssistantMessageId(state.nodeId, timestamp, finalContent),
            role = DmRole.ASSISTANT,
            content = finalContent,
            timestamp = timestamp,
            nodeId = state.nodeId,
            nodeName = state.nodeName,
            blocks = state.blocks.toList(),
        )
    }
}
