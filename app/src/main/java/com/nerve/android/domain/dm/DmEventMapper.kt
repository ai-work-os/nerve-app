package com.nerve.android.domain.dm

import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DmEventMapper {
    fun map(event: NerveEvent): DmMappedEvent = when (event) {
        is NerveEvent.NodeUpdate -> mapNodeUpdate(event)
        is NerveEvent.NodeStatusChanged -> {
            if (event.status == "idle") {
                DmMappedEvent.NodeIdle(
                    nodeId = event.nodeId,
                    timestamp = parseStableTimestamp(event.detail, event.nodeId, "idle"),
                )
            } else {
                DmMappedEvent.Ignore
            }
        }

        else -> DmMappedEvent.Ignore
    }

    private fun mapNodeUpdate(event: NerveEvent.NodeUpdate): DmMappedEvent {
        val update = event.detail["update"]?.jsonObject ?: return ignore(event.nodeId, "missing_update")
        val kind = update["sessionUpdate"]?.jsonPrimitive?.content ?: return ignore(event.nodeId, "missing_kind")
        val timestamp = parseStableTimestamp(event.detail, event.nodeId, kind)
        val text = update.extractText()
        return when (kind) {
            "user_message" -> {
                if (text.isNullOrBlank()) return ignore(event.nodeId, "empty_user")
                DmMappedEvent.UserMessage(
                    nodeId = event.nodeId,
                    nodeName = event.name,
                    content = text,
                    timestamp = timestamp,
                    messageId = buildUserMessageId(event.nodeId, timestamp, text),
                )
            }

            "agent_message_start" -> DmMappedEvent.AgentMessageStart(
                nodeId = event.nodeId,
                nodeName = event.name,
                timestamp = timestamp,
                messageId = buildStreamMessageId(event.nodeId, timestamp),
            )

            "agent_message_chunk" -> {
                if (text.isNullOrEmpty()) return ignore(event.nodeId, "empty_chunk")
                DmMappedEvent.AgentMessageChunk(
                    nodeId = event.nodeId,
                    text = text,
                    timestamp = timestamp,
                )
            }

            "agent_message_end" -> DmMappedEvent.AgentMessageEnd(
                nodeId = event.nodeId,
                nodeName = event.name,
                timestamp = timestamp,
                fallbackText = text,
            )

            else -> ignore(event.nodeId, "unknown_update")
        }
    }

    private fun ignore(nodeId: String, reason: String): DmMappedEvent.Ignore {
        Logger.d("DmEventMapper", "dm ignore key=$nodeId reason=$reason")
        return DmMappedEvent.Ignore
    }
}

internal fun buildUserMessageId(nodeId: String, timestamp: Long, content: String): String =
    "user:$nodeId:$timestamp:${stableHash(content)}"

internal fun buildAssistantMessageId(nodeId: String, timestamp: Long, content: String): String =
    "assistant:$nodeId:$timestamp:${stableHash(content)}"

internal fun buildStreamMessageId(nodeId: String, timestamp: Long): String = "stream:$nodeId:$timestamp"

private fun stableHash(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
}

internal fun parseStableTimestamp(detail: JsonObject, nodeId: String, seed: String): Long {
    val ts = detail["ts"]?.jsonPrimitive?.content
    if (ts != null) {
        return Instant.parse(ts).toEpochMilli()
    }
    return stableFallbackTimestamp("$nodeId:$seed:${detail.toString()}")
}

private fun stableFallbackTimestamp(seed: String): Long =
    seed.fold(0L) { acc, c -> (acc * 131 + c.code) and Long.MAX_VALUE }

private fun JsonObject.extractText(): String? {
    val content = this["content"] ?: return null
    return when (content) {
        is JsonObject -> content["text"]?.jsonPrimitive?.content
        else -> content.jsonPrimitive.content
    }
}
