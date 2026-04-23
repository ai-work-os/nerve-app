package com.nerve.android.integration

import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.RealNerveClient
import com.nerve.android.transport.ServerConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

/**
 * Integration tests that connect to a real nerve server.
 * The server is started/stopped automatically by NerveServerExtension.
 *
 * Uses runBlocking because real network I/O requires real time.
 */
class NerveIntegrationTest {

    companion object {
        @JvmField
        @RegisterExtension
        val server = NerveServerExtension()
    }

    private lateinit var client: RealNerveClient

    private fun serverConfig() = ServerConfig(
        id = "test-${UUID.randomUUID().toString().take(6)}",
        name = "test-server",
        address = server.wsUrl,
    )

    private fun registration() = ClientRegistration(
        name = "test-ui-${UUID.randomUUID().toString().take(6)}",
    )

    @AfterEach
    fun tearDown() {
        if (::client.isInitialized) {
            runBlocking { client.disconnect() }
        }
    }

    /**
     * Test 1: WebSocket connects and registers successfully.
     */
    @Test
    @Timeout(15)
    fun connectAndRegister() = runBlocking {
        client = RealNerveClient(endpoint = server.wsUrl)
        client.connect(serverConfig(), registration())

        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)

        // Verify we can call the server (listNodes should return at least ourselves)
        val nodes = client.listNodes()
        assertTrue(nodes.isNotEmpty(), "node list should contain at least our registered node")
    }

    /**
     * Test 2: Channel post and history replay.
     */
    @Test
    @Timeout(15)
    fun channelPostAndHistory() = runBlocking {
        client = RealNerveClient(endpoint = server.wsUrl)
        client.connect(serverConfig(), registration())

        // Create a channel
        val channelName = "test-${UUID.randomUUID().toString().take(6)}"
        val createResult = client.call(
            "channel.create",
            buildJsonObject { put("name", channelName) },
        ).jsonObject
        val channelId = createResult["channelId"]!!.jsonPrimitive.content
        assertTrue(channelId.isNotEmpty(), "channelId should not be empty")

        // Join channel
        client.call("channel.join", buildJsonObject { put("channelId", channelId) })

        // Post a message
        val testContent = "integration-test-${System.currentTimeMillis()}"
        val postResult = client.call(
            "channel.post",
            buildJsonObject {
                put("channelId", channelId)
                put("content", testContent)
            },
        ).jsonObject
        val postedMsg = postResult["message"]!!.jsonObject
        assertEquals(testContent, postedMsg["content"]!!.jsonPrimitive.content)

        // Retrieve history and verify
        val historyResult = client.call(
            "channel.history",
            buildJsonObject {
                put("channelId", channelId)
                put("limit", 10)
            },
        ).jsonObject
        val messages = historyResult["messages"]!!.jsonArray
        assertTrue(messages.isNotEmpty(), "history should not be empty")
        val found = messages.any {
            it.jsonObject["content"]?.jsonPrimitive?.content == testContent
        }
        assertTrue(found, "history should contain our posted message")
    }

    /**
     * Test 3: Post message and receive channel.message event on another connection.
     */
    @Test
    @Timeout(15)
    fun postMessageAndReceiveEvent() = runBlocking {
        // Client 1: create channel and listen for events
        client = RealNerveClient(endpoint = server.wsUrl)
        client.connect(serverConfig(), registration())

        val channelName = "evt-${UUID.randomUUID().toString().take(6)}"
        val createResult = client.call(
            "channel.create",
            buildJsonObject { put("name", channelName) },
        ).jsonObject
        val channelId = createResult["channelId"]!!.jsonPrimitive.content
        client.call("channel.join", buildJsonObject { put("channelId", channelId) })

        // Client 2: join same channel and post
        val client2 = RealNerveClient(endpoint = server.wsUrl)
        try {
            client2.connect(serverConfig(), registration())
            client2.call("channel.join", buildJsonObject { put("channelId", channelId) })

            val testContent = "event-test-${System.currentTimeMillis()}"

            // Start listening on client 1 before posting
            val eventDeferred = async(Dispatchers.Default) {
                withTimeout(10_000) {
                    client.events
                        .filterIsInstance<NerveEvent.ChannelMessage>()
                        .first {
                            it.channelId == channelId &&
                                it.payload["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content == testContent
                        }
                }
            }

            delay(200) // ensure listener is active

            // Post from client 2
            client2.call(
                "channel.post",
                buildJsonObject {
                    put("channelId", channelId)
                    put("content", testContent)
                },
            )

            // Wait for event on client 1
            val event = eventDeferred.await()
            assertEquals(channelId, event.channelId)
            assertEquals(testContent, event.payload["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content)
        } finally {
            client2.disconnect()
        }
    }
}
