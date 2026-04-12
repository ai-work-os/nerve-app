package com.nerve.android.transport

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object RpcSerializer {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encodeRequest(request: RpcRequest): String = json.encodeToString(request)

    fun decodeResponse(raw: String): RpcResponse = json.decodeFromString(raw)

    fun parseNotification(method: String, params: JsonObject): NerveEvent? = when (method) {
        "channel.deleted", "channel.closed" -> {
            val channelId = params.string("channelId") ?: return null
            NerveEvent.ChannelClosed(channelId, params.string("name"))
        }

        "node.update" -> {
            val nodeId = params.string("nodeId") ?: return null
            val name = params.string("name") ?: return null
            NerveEvent.NodeUpdate(
                nodeId = nodeId,
                name = name,
                detail = buildJsonObject {
                    params.forEach { (key, value) ->
                        if (key != "nodeId" && key != "name") {
                            put(key, value)
                        }
                    }
                },
            )
        }

        "message_snapshot" -> {
            val nodeId = params.string("nodeId") ?: return null
            val name = params.string("name") ?: return null
            val messagesJson = params["messages"] as? JsonArray ?: JsonArray(emptyList())
            val messages = runCatching {
                messagesJson.map { json.decodeFromJsonElement(SnapshotMessage.serializer(), it) }
            }.getOrElse { emptyList() }
            NerveEvent.MessageSnapshot(nodeId = nodeId, name = name, messages = messages)
        }

        "channel.message" -> {
            val channelId = params.string("channelId") ?: return null
            NerveEvent.ChannelMessage(channelId, params.string("messageId"), params)
        }

        "channel.mention" -> {
            val channelId = params.string("channelId") ?: return null
            NerveEvent.ChannelMention(channelId, params)
        }

        "channel.nodeJoined" -> {
            val channelId = params.string("channelId") ?: return null
            val nodeId = params.string("nodeId") ?: return null
            NerveEvent.NodeJoined(channelId, nodeId, params.string("nodeName"))
        }

        "channel.nodeLeft" -> {
            val channelId = params.string("channelId") ?: return null
            val nodeId = params.string("nodeId") ?: return null
            NerveEvent.NodeLeft(channelId, nodeId, params.string("nodeName"))
        }

        "node.statusChanged" -> {
            val nodeId = params.string("nodeId") ?: return null
            val status = params.string("status") ?: return null
            NerveEvent.NodeStatusChanged(nodeId, status, params)
        }

        "node.registered" -> {
            val nodeId = params.string("nodeId") ?: return null
            NerveEvent.NodeRegistered(nodeId, params.string("name"))
        }

        "node.stopped" -> {
            val nodeId = params.string("nodeId") ?: return null
            NerveEvent.NodeStopped(nodeId)
        }

        "channel.created" -> {
            val channelId = params.string("channelId") ?: return null
            val name = params.string("name") ?: return null
            NerveEvent.ChannelCreated(channelId, name)
        }

        else -> null
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
}
