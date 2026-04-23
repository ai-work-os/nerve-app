package com.nerve.android.transport

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NerveClientConnectTest {
    @Test
    fun `connect sends register and becomes connected after register response`() = runTest {
        val server = FakeNerveWsServer()
        val client = RealNerveClient(server.url, backoffStrategy = FakeBackoffStrategy())
        try {
            server.enqueueRegisterSuccess()
            client.connect(
                server = ServerConfig(id = "s1", name = "home", address = server.address()),
                registration = ClientRegistration(name = "android-ui"),
            )

            val register = server.takeClientMessageAsJson()
            assertEquals("node.register", register.method)
            assertEquals("android-ui", register.params["name"])
            assertEquals(listOf("ui"), register.params["capabilities"])
            assertEquals("operator", register.params["permissions"])
            assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        } finally {
            client.disconnect()
        }
    }
}
