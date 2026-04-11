package com.nerve.android.transport

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NerveClientSubscribeReplayTest {
    @Test
    fun `reconnect re-registers before replaying subscriptions and replay notifications reach events`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy(delayMs = 0))
        try {
            server.enqueueRegisterSuccess()
            client.connect(ServerConfig("s1", "home", server.address()), ClientRegistration(name = "android-ui"))
            server.enqueueSubscribeSuccess("node-a")
            client.subscribe("node-a")

            server.closeConnection()
            server.enqueueRegisterSuccess()
            server.enqueueSubscribeSuccess("node-a")

            val replay = async { client.events.first() }
            advanceUntilIdle()
            server.sendNotification(
                method = "node.update",
                params = """
                {
                  "nodeId":"node-a",
                  "name":"bot",
                  "kind":"agent_message",
                  "content":"replay"
                }
                """.trimIndent(),
            )

            val event = assertIs<NerveEvent.NodeUpdate>(replay.await())
            assertEquals("node-a", event.nodeId)
            assertEquals("replay", event.detail["content"]?.toString()?.trim('"'))

            val methods = server.receivedMethods()
            val firstRegister = methods.indexOfLast { it == "node.register" }
            val firstSubscribe = methods.indexOfLast { it == "node.subscribe" }
            assertTrue(firstRegister >= 0)
            assertTrue(firstSubscribe > firstRegister)
        } finally {
            client.disconnect()
        }
    }
}
