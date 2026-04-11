package com.nerve.android.transport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NerveClientReconnectTest {
    @Test
    fun `disconnect by server triggers reconnect and re-register with previous registration`() = runTest {
        val server = FakeNerveWsServer()
        val backoff = FakeBackoffStrategy(delayMs = 0)
        val client = RealNerveClient(server.url, backoffStrategy = backoff)
        try {
            server.enqueueRegisterSuccess()
            client.connect(
                ServerConfig("s1", "home", server.address()),
                ClientRegistration(name = "android-ui", capabilities = listOf("ui"), permissions = "operator"),
            )
            assertEquals("node.register", server.takeClientMessageAsJson().method)

            server.closeConnection()
            assertEquals(ConnectionState.RECONNECTING, client.connectionState.value)

            server.enqueueRegisterSuccess()
            advanceUntilIdle()
            assertEquals("node.register", server.takeClientMessageAsJson().method)
            repeat(20) {
                if (client.connectionState.value == ConnectionState.CONNECTED) return@repeat
                advanceUntilIdle()
                Thread.sleep(10)
            }

            assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
            assertEquals(1, backoff.invocationCount)
        } finally {
            client.disconnect()
        }
    }
}
