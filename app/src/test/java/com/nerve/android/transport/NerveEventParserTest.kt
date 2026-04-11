package com.nerve.android.transport

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse

class NerveEventParserTest {
    @Test
    fun `channel deleted maps to channel closed`() {
        val event = RpcSerializer.parseNotification(
            method = "channel.deleted",
            params = buildJsonObject {
                put("channelId", "c1")
                put("name", "general")
            },
        )

        val closed = assertIs<NerveEvent.ChannelClosed>(event)
        assertEquals("c1", closed.channelId)
        assertEquals("general", closed.name)
    }

    @Test
    fun `node update keeps only detail fields`() {
        val event = RpcSerializer.parseNotification(
            method = "node.update",
            params = buildJsonObject {
                put("nodeId", "n1")
                put("name", "bot")
                put("kind", "agent_message")
                put("content", "hello")
            },
        )

        val update = assertIs<NerveEvent.NodeUpdate>(event)
        assertEquals("n1", update.nodeId)
        assertEquals("bot", update.name)
        assertEquals("agent_message", update.detail.getValue("kind").jsonPrimitive.content)
        assertEquals("hello", update.detail.getValue("content").jsonPrimitive.content)
        assertFalse(update.detail.containsKey("nodeId"))
        assertFalse(update.detail.containsKey("name"))
    }

    @Test
    fun `channel node events read nodeName`() {
        val joined = RpcSerializer.parseNotification(
            method = "channel.nodeJoined",
            params = buildJsonObject {
                put("channelId", "c1")
                put("nodeId", "n1")
                put("nodeName", "alice")
            },
        )
        val left = RpcSerializer.parseNotification(
            method = "channel.nodeLeft",
            params = buildJsonObject {
                put("channelId", "c1")
                put("nodeId", "n1")
                put("nodeName", "alice")
            },
        )

        assertEquals("alice", assertIs<NerveEvent.NodeJoined>(joined).name)
        assertEquals("alice", assertIs<NerveEvent.NodeLeft>(left).name)
    }

    @Test
    fun `known notification with missing required field is ignored`() {
        val event = RpcSerializer.parseNotification(
            method = "node.update",
            params = buildJsonObject {
                put("name", "bot")
                put("kind", "agent_message")
            },
        )

        assertNull(event)
    }

    @Test
    fun `unknown notification is ignored`() {
        val event = RpcSerializer.parseNotification(
            method = "mystery.event",
            params = JsonObject(emptyMap()),
        )

        assertNull(event)
    }
}
