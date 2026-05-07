package com.nerve.android.domain.server

import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ServerRegistryTest {
    @Test
    fun `start aggregates nodes channels and scoped events by server id`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("s1", "Home", "10.0.0.1:4800"),
                ServerConfig("s2", "Lab", "10.0.0.2:4800"),
            ),
        )
        val clientA = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n1", "bot-a")))
            channelsResult = Result.success(listOf(ChannelInfo("c1", "general-a")))
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

        val eventDeferred = async { registry.events.first() }
        registry.start(ClientRegistration(name = "android-ui"))
        clientB.events.emit(NerveEvent.ChannelCreated(channelId = "c2", name = "general-b"))

        assertEquals(listOf("s1", "s2"), registry.connections.value.map { it.serverId })
        assertEquals(listOf("s1:n1", "s2:n2"), registry.nodes.value.map { "${it.serverId}:${it.node.id}" })
        assertEquals(listOf("s1:c1", "s2:c2"), registry.channels.value.map { "${it.serverId}:${it.channel.id}" })
        assertEquals("s2", eventDeferred.await().serverId)
        assertSame(clientA, registry.client("s1"))
    }

    @Test
    fun `targeted refresh only touches requested server when two servers exist`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("s1", "Home", "10.0.0.1:4800"),
                ServerConfig("s2", "Lab", "10.0.0.2:4800"),
            ),
        )
        val clientA = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n1", "bot-a")))
            channelsResult = Result.success(listOf(ChannelInfo("c1", "general-a")))
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
        clientA.listNodesCalls = 0
        clientA.listChannelsCalls = 0
        clientB.listNodesCalls = 0
        clientB.listChannelsCalls = 0

        registry.refresh("s1")

        assertEquals(1, clientA.listNodesCalls)
        assertEquals(1, clientA.listChannelsCalls)
        assertEquals(0, clientB.listNodesCalls)
        assertEquals(0, clientB.listChannelsCalls)
        assertEquals(listOf("s1:n1", "s2:n2"), registry.nodes.value.map { "${it.serverId}:${it.node.id}" })
        assertEquals(listOf("s1:c1", "s2:c2"), registry.channels.value.map { "${it.serverId}:${it.channel.id}" })
    }

    @Test
    fun `refresh target only and same id override recreates client`() = runTest {
        val store = FakeServerConfigStore(listOf(ServerConfig("s1", "Home", "10.0.0.1:4800")))
        val oldClient = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n1", "bot-a")))
            channelsResult = Result.success(listOf(ChannelInfo("c1", "general-a")))
        }
        val newClient = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n9", "bot-new")))
            channelsResult = Result.success(listOf(ChannelInfo("c9", "general-new")))
        }
        val factory = FakeNerveClientFactory(
            mutableMapOf("s1" to ArrayDeque(listOf(oldClient, newClient))),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        registry.start(ClientRegistration(name = "android-ui"))
        oldClient.listNodesCalls = 0
        oldClient.listChannelsCalls = 0

        registry.refresh("s1")
        registry.addServer(ServerConfig("s1", "Home 2", "10.0.0.9:4800"))

        assertEquals(1, oldClient.listNodesCalls)
        assertEquals(1, oldClient.listChannelsCalls)
        assertEquals(1, oldClient.disconnectCalls)
        assertEquals(2, factory.createCalls.count { it == "s1" })
        assertSame(newClient, registry.client("s1"))
        assertEquals(listOf(ConnectionState.CONNECTED), registry.connections.value.map { it.state })
        assertEquals(listOf("n9"), registry.nodes.value.map { it.node.id })
    }

    @Test
    fun `triggerReconnectAll wakes every connected client`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("s1", "Home", "10.0.0.1:4800"),
                ServerConfig("s2", "Lab", "10.0.0.2:4800"),
            ),
        )
        val clientA = FakeNerveClient()
        val clientB = FakeNerveClient()
        val factory = FakeNerveClientFactory(
            mutableMapOf(
                "s1" to ArrayDeque(listOf(clientA)),
                "s2" to ArrayDeque(listOf(clientB)),
            ),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        registry.start(ClientRegistration(name = "android-ui"))

        registry.triggerReconnectAll()

        assertEquals(1, clientA.triggerReconnectCalls)
        assertEquals(1, clientB.triggerReconnectCalls)
    }
}

internal class FakeServerConfigStore(
    initial: List<ServerConfig>,
) : ServerConfigStore {
    private val configs = initial.toMutableList()

    override suspend fun load(): List<ServerConfig> = configs.toList()

    override suspend fun saveAll(configs: List<ServerConfig>) {
        this.configs.clear()
        this.configs.addAll(configs)
    }

    override suspend fun upsert(config: ServerConfig) {
        configs.removeAll { it.id == config.id }
        configs.add(config)
    }

    override suspend fun remove(serverId: String) {
        configs.removeAll { it.id == serverId }
    }
}
