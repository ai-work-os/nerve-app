package com.nerve.android.domain.server

import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerRegistryIsolationTest {
    @Test
    fun `start exposes s2 initial nodes and channels even when s1 connect fails`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("s1", "Home", "10.0.0.1:4800"),
                ServerConfig("s2", "Lab", "10.0.0.2:4800"),
            ),
        )
        val clientA = FakeNerveClient().apply {
            connectResult = Result.failure(IllegalStateException("connect failed"))
        }
        val clientB = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n2", "bot-b")))
            channelsResult = Result.success(listOf(ChannelInfo("c2", "general-b")))
        }
        val factory = FakeNerveClientFactory(
            mutableMapOf(
                "s1" to ArrayDeque(listOf(clientA)),
                "s2" to ArrayDeque(listOf(clientB)),
            ),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        registry.start(ClientRegistration(name = "android-ui"))

        assertEquals(listOf("s2:n2"), registry.nodes.value.map { "${it.serverId}:${it.node.id}" })
        assertEquals(listOf("s2:c2"), registry.channels.value.map { "${it.serverId}:${it.channel.id}" })
    }

    @Test
    fun `s1 connect failure does not block s2 start and refresh`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("s1", "Home", "10.0.0.1:4800"),
                ServerConfig("s2", "Lab", "10.0.0.2:4800"),
            ),
        )
        val clientA = FakeNerveClient().apply {
            connectResult = Result.failure(IllegalStateException("connect failed"))
        }
        val clientB = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n2", "bot-b")))
            channelsResult = Result.success(listOf(ChannelInfo("c2", "general-b")))
        }
        val factory = FakeNerveClientFactory(
            mutableMapOf(
                "s1" to ArrayDeque(listOf(clientA)),
                "s2" to ArrayDeque(listOf(clientB)),
            ),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        registry.start(ClientRegistration(name = "android-ui"))
        registry.refresh("s2")

        assertEquals(1, clientA.connectCalls)
        assertEquals(1, clientB.connectCalls)
        assertEquals(2, clientB.listNodesCalls)
        assertEquals(2, clientB.listChannelsCalls)
        assertEquals(listOf("s2"), registry.nodes.value.map { it.serverId })
        assertEquals(listOf("s2"), registry.channels.value.map { it.serverId })
    }

    @Test
    fun `server failure remove and repeated start do not affect others`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("s1", "Home", "10.0.0.1:4800"),
                ServerConfig("s2", "Lab", "10.0.0.2:4800"),
            ),
        )
        val clientA = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n1", "bot-a")))
            channelsResult = Result.failure(IllegalStateException("boom"))
        }
        val clientB = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n2", "bot-b")))
            channelsResult = Result.success(listOf(ChannelInfo("c2", "general-b")))
        }
        val factory = FakeNerveClientFactory(
            mutableMapOf(
                "s1" to ArrayDeque(listOf(clientA)),
                "s2" to ArrayDeque(listOf(clientB)),
            ),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        registry.start(ClientRegistration(name = "android-ui"))
        registry.start(ClientRegistration(name = "android-ui"))
        clientA.connectionState.value = ConnectionState.RECONNECTING
        registry.refresh("s1")
        registry.removeServer("s1")

        assertEquals(1, factory.createCalls.count { it == "s1" })
        assertEquals(1, factory.createCalls.count { it == "s2" })
        assertEquals(listOf("s2"), registry.connections.value.map { it.serverId })
        assertEquals(listOf("s2:n2"), registry.nodes.value.map { "${it.serverId}:${it.node.id}" })
        assertEquals(listOf("s2:c2"), registry.channels.value.map { "${it.serverId}:${it.channel.id}" })
        assertEquals(1, clientA.disconnectCalls)
        assertEquals(1, clientB.connectCalls)
    }
}
