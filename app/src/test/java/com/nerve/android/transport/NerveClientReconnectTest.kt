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
            val firstRegister = server.takeClientMessageAsJson()
            assertEquals("node.register", firstRegister.method)
            assertEquals(true, firstRegister.params["persistent"])

            server.closeConnection()
            assertEquals(ConnectionState.RECONNECTING, client.connectionState.value)

            server.enqueueRegisterSuccess()
            advanceUntilIdle()
            val secondRegister = server.takeClientMessageAsJson()
            assertEquals("node.register", secondRegister.method)
            assertEquals(true, secondRegister.params["persistent"])
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

    @Test
    fun `stale close from previous socket does not fail reconnect registration`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy(delayMs = 0))
        try {
            server.enqueueRegisterSuccess()
            client.connect(
                ServerConfig("s1", "home", server.address()),
                ClientRegistration(name = "android-ui"),
            )
            assertEquals("node.register", server.takeClientMessageAsJson().method)

            server.closeConnection()
            assertEquals(ConnectionState.RECONNECTING, client.connectionState.value)

            server.enqueueRegisterSuccessAfterClosingConnection(connectionIndex = 0)
            advanceUntilIdle()
            val secondRegister = server.takeClientMessageAsJson()
            assertEquals("node.register", secondRegister.method)
            repeat(20) {
                if (client.connectionState.value == ConnectionState.CONNECTED) return@repeat
                advanceUntilIdle()
                Thread.sleep(10)
            }

            assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        } finally {
            client.disconnect()
        }
    }
}
