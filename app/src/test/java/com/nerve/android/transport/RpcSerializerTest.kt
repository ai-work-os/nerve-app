package com.nerve.android.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class RpcSerializerTest {
    @Test
    fun `request serializes with jsonrpc id method and params`() {
        val request = RpcRequest(
            id = 7L,
            method = "node.list",
            params = buildJsonObject { put("scope", "all") },
        )

        val encoded = RpcSerializer.encodeRequest(request)
        val json = Json.parseToJsonElement(encoded).jsonObject

        assertEquals("2.0", json.getValue("jsonrpc").jsonPrimitive.content)
        assertEquals(7L, json.getValue("id").jsonPrimitive.long)
        assertEquals("node.list", json.getValue("method").jsonPrimitive.content)
        assertEquals("all", json.getValue("params").jsonObject.getValue("scope").jsonPrimitive.content)
    }

    @Test
    fun `response deserializes result and error`() {
        val ok = """{"jsonrpc":"2.0","id":3,"result":{"ok":true}}"""
        val err = """{"jsonrpc":"2.0","id":4,"error":{"code":-32000,"message":"boom"}}"""

        val okResponse = RpcSerializer.decodeResponse(ok)
        val errResponse = RpcSerializer.decodeResponse(err)

        assertEquals(3L, okResponse.id)
        assertEquals(true, okResponse.result!!.jsonObject.getValue("ok").jsonPrimitive.boolean)
        assertEquals(4L, errResponse.id)
        assertEquals(-32000, errResponse.error!!.code)
        assertEquals("boom", errResponse.error!!.message)
    }

    @Test
    fun `parseNotification message_snapshot empty`() {
        val params = buildJsonObject {
            put("nodeId", "n1")
            put("name", "bob")
            put("messages", buildJsonArray {})
        }
        val evt = RpcSerializer.parseNotification("message_snapshot", params)
        val snapshot = assertIs<NerveEvent.MessageSnapshot>(evt)
        assertEquals("n1", snapshot.nodeId)
        assertEquals("bob", snapshot.name)
        assertEquals(0, snapshot.messages.size)
    }

    @Test
    fun `parseNotification message_snapshot with messages`() {
        val params = buildJsonObject {
            put("nodeId", "n1")
            put("name", "bob")
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("id", "m1")
                    put("nodeId", "n1")
                    put("role", "user")
                    put("sender", "renjinxi")
                    put("text", "hi")
                    put("ts", 1710000000000.0)
                })
                add(buildJsonObject {
                    put("id", "m2")
                    put("nodeId", "n1")
                    put("role", "agent")
                    put("sender", "bob")
                    put("text", "hello")
                    put("ts", 1710000001000.0)
                })
            })
        }
        val evt = RpcSerializer.parseNotification("message_snapshot", params)
        val snapshot = assertIs<NerveEvent.MessageSnapshot>(evt)
        assertEquals(2, snapshot.messages.size)
        assertEquals("user", snapshot.messages[0].role)
        assertEquals("hi", snapshot.messages[0].text)
        assertEquals("agent", snapshot.messages[1].role)
        assertEquals("hello", snapshot.messages[1].text)
        assertEquals("bob", snapshot.messages[1].sender)
    }

    @Test
    fun `server config normalizes host port but keeps ws url unchanged`() {
        assertEquals("ws://127.0.0.1:4800", ServerConfig("s1", "local", "127.0.0.1:4800").webSocketUrl())
        assertEquals("ws://host:4800", ServerConfig("s1", "local", "ws://host:4800").webSocketUrl())
    }
}
