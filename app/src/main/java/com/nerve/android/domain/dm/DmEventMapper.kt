package com.nerve.android.domain.dm

import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DmEventMapper {
    fun map(event: NerveEvent): DmMappedEvent = try {
        when (event) {
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
    } catch (e: Exception) {
        Logger.w("DmEventMapper", "dm map error: ${e.message}")
        DmMappedEvent.Ignore
    }

    private fun mapNodeUpdate(event: NerveEvent.NodeUpdate): DmMappedEvent {
        val update = event.detail["update"]?.jsonObject ?: return ignore(event.nodeId, "missing_update")
        val kind = runCatching { update["sessionUpdate"]?.jsonPrimitive?.content }.getOrNull()
            ?: return ignore(event.nodeId, "missing_kind")
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

            "agent_thought_chunk" -> {
                if (text.isNullOrEmpty()) return ignore(event.nodeId, "empty_thought")
                DmMappedEvent.AgentThoughtChunk(
                    nodeId = event.nodeId,
                    text = text,
                    timestamp = timestamp,
                )
            }

            "agent_thought_end" -> DmMappedEvent.AgentThoughtEnd(
                nodeId = event.nodeId,
                timestamp = timestamp,
            )

            "tool_call" -> mapToolCall(update, event.nodeId, timestamp)

            "tool_call_update" -> mapToolCallUpdate(update, event.nodeId, timestamp)

            else -> ignore(event.nodeId, "unknown_update")
        }
    }

    private fun mapToolCall(update: JsonObject, nodeId: String, timestamp: Long): DmMappedEvent {
        // ACP flat format: toolCallId, _meta.claudeCode.toolName, rawInput
        val acpId = update["toolCallId"]?.jsonPrimitive?.content
        if (acpId != null) {
            val toolName = update["_meta"]?.jsonObject
                ?.get("claudeCode")?.jsonObject
                ?.get("toolName")?.jsonPrimitive?.content
                ?: update["title"]?.jsonPrimitive?.content
                ?: ""
            val rawInput = update["rawInput"]
            val input = when (rawInput) {
                is kotlinx.serialization.json.JsonPrimitive -> rawInput.content
                else -> rawInput?.toString() ?: ""
            }
            return DmMappedEvent.ToolCall(nodeId = nodeId, toolId = acpId, toolName = toolName, input = input, timestamp = timestamp)
        }
        // Legacy nested format: toolCall.{id, name, input}
        val legacy = update["toolCall"] as? JsonObject
        if (legacy != null) {
            return DmMappedEvent.ToolCall(
                nodeId = nodeId,
                toolId = legacy["id"]?.jsonPrimitive?.content ?: "",
                toolName = legacy["name"]?.jsonPrimitive?.content ?: "",
                input = legacy["input"]?.toString() ?: "",
                timestamp = timestamp,
            )
        }
        // Original content-based format
        val content = update["content"] as? JsonObject ?: return ignore(nodeId, "missing_tool_call")
        return DmMappedEvent.ToolCall(
            nodeId = nodeId,
            toolId = content["id"]?.jsonPrimitive?.content ?: "",
            toolName = content["name"]?.jsonPrimitive?.content ?: "",
            input = content["input"]?.toString() ?: "",
            timestamp = timestamp,
        )
    }

    private fun mapToolCallUpdate(update: JsonObject, nodeId: String, timestamp: Long): DmMappedEvent {
        // ACP flat format: toolCallId, status, content array
        val acpId = update["toolCallId"]?.jsonPrimitive?.content
        if (acpId != null) {
            val status = update["status"]?.jsonPrimitive?.content ?: ""
            val output = (update["content"] as? JsonArray)?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                (obj["content"] as? JsonObject)?.get("text")?.jsonPrimitive?.content
            }?.joinToString("")?.ifEmpty { null }
            return DmMappedEvent.ToolCallUpdate(nodeId = nodeId, toolId = acpId, status = status, output = output, timestamp = timestamp)
        }
        // Legacy nested format: toolCallUpdate.{id, status, content}
        val legacy = update["toolCallUpdate"] as? JsonObject
        if (legacy != null) {
            return DmMappedEvent.ToolCallUpdate(
                nodeId = nodeId,
                toolId = legacy["id"]?.jsonPrimitive?.content ?: "",
                status = legacy["status"]?.jsonPrimitive?.content ?: "",
                output = legacy["content"]?.jsonPrimitive?.content,
                timestamp = timestamp,
            )
        }
        // Original content-based format
        val content = update["content"] as? JsonObject ?: return ignore(nodeId, "missing_tool_update")
        return DmMappedEvent.ToolCallUpdate(
            nodeId = nodeId,
            toolId = content["id"]?.jsonPrimitive?.content ?: "",
            status = content["status"]?.jsonPrimitive?.content ?: "",
            output = content["output"]?.jsonPrimitive?.content,
            timestamp = timestamp,
        )
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
        is kotlinx.serialization.json.JsonArray -> {
            content.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val type = obj["type"]?.jsonPrimitive?.content
                if (type == "text") obj["text"]?.jsonPrimitive?.content else null
            }.joinToString("").ifEmpty { null }
        }
        else -> runCatching { content.jsonPrimitive.content }.getOrNull()
    }
}
