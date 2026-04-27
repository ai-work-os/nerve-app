package com.nerve.android.transport

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NerveClientRpcTest {
    @Test
    fun `listNodes sends node list and parses typed result`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy())
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))

            server.enqueueRpcResult("node.list") {
                """
                {"nodes":[{"id":"n1","name":"bot-1"},{"id":"n2","name":"bot-2"}]}
                """.trimIndent()
            }

            val nodes = client.listNodes()

            assertEquals(listOf("n1", "n2"), nodes.map { it.id })
            assertEquals(listOf("bot-1", "bot-2"), nodes.map { it.name })
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `concurrent calls route by request id`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy())
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))

            val nodesDeferred = async { client.listNodes() }
            val channelsDeferred = async { client.listChannels() }

            server.replyOutOfOrder(
                firstMethod = "channel.list",
                firstResult = """{"channels":[{"id":"c1","name":"general"}]}""",
                secondMethod = "node.list",
                secondResult = """{"nodes":[{"id":"n1","name":"bot"}]}""",
            )

            assertEquals(listOf("n1"), nodesDeferred.await().map { it.id })
            assertEquals(listOf("c1"), channelsDeferred.await().map { it.id })
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `error response becomes server error`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy())
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))
            server.enqueueRpcError("node.list", code = -32001, message = "denied")

            val error = assertFailsWith<RpcException.ServerError> { client.listNodes() }
            assertEquals(-32001, error.code)
            assertEquals("denied", error.message)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `timeout becomes request timeout`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(
            server.url,
            requestTimeoutMs = 50,
            backoffStrategy = FakeBackoffStrategy(),
        )
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))

            assertFailsWith<RpcException.RequestTimeout> { client.listNodes() }
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `call while reconnecting throws transport disconnected`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy())
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))
            server.closeConnection()

            assertFailsWith<RpcException.TransportDisconnected> { client.listNodes() }
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `prompt parses stop reason`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy())
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))
            server.takeClientMessageAsJson()
            server.enqueueRpcResult("node.prompt") {
                """{"stopReason":"end_turn"}"""
            }

            val result = client.prompt("n1", "hello")

            assertEquals("end_turn", result.stopReason)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun `prompt with image sends attachment payload`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy())
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))
            server.takeClientMessageAsJson()
            server.enqueueRpcResult("node.prompt") {
                """{"stopReason":"end_turn"}"""
            }

            client.prompt("n1", "look", PromptAttachment.Image(mimeType = "image/png", data = "abc123"))

            val params = server.takeClientMessageAsJson().params
            assertEquals("n1", params["nodeId"])
            assertEquals("look", params["content"])
            @Suppress("UNCHECKED_CAST")
            val image = (params["attachments"] as List<Map<String, Any?>>).first()
            assertEquals("image", image["type"])
            assertEquals("image/png", image["mimeType"])
            assertEquals("abc123", image["data"])
        } finally {
            client.disconnect()
        }
    }
}
