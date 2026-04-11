package com.nerve.android.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
    fun `server config normalizes host port but keeps ws url unchanged`() {
        assertEquals("ws://127.0.0.1:4800", ServerConfig("s1", "local", "127.0.0.1:4800").webSocketUrl())
        assertEquals("ws://host:4800", ServerConfig("s1", "local", "ws://host:4800").webSocketUrl())
    }
}
