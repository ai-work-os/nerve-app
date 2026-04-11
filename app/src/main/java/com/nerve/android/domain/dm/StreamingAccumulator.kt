package com.nerve.android.domain.dm

import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap

interface StreamingAccumulator {
    fun onStart(key: DmKey, nodeId: String, nodeName: String, messageId: String, timestamp: Long)
    fun onChunk(key: DmKey, text: String, timestamp: Long)
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

    override fun onChunk(key: DmKey, text: String, timestamp: Long) {
        val state = activeStreams[key] ?: return
        state.text.append(text)
        state.lastEventAt = timestamp
        Logger.d("StreamingAccumulator", "stream chunk key=${key.value} len=${text.length} ts=$timestamp")
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
        if (content.isBlank()) return null
        return DmMessage(
            id = buildAssistantMessageId(state.nodeId, timestamp, content),
            role = DmRole.ASSISTANT,
            content = content,
            timestamp = timestamp,
            nodeId = state.nodeId,
            nodeName = state.nodeName,
        )
    }
}
