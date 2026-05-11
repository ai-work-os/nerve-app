package com.nerve.android.domain.server

import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerRegistryReorderTest {
    @Test
    fun `reorderServers rearranges servers by orderedIds`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
                ServerConfig("c", "C", "10.0.0.3:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("c", "a", "b"))

        assertEquals(listOf("c", "a", "b"), registry.servers.value.map { it.id })
    }

    @Test
    fun `reorderServers persists new order to store`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("b", "a"))

        assertEquals(listOf("b", "a"), store.load().map { it.id })
    }

    @Test
    fun `reorderServers ignores unknown ids`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("ghost", "b", "a"))

        assertEquals(listOf("b", "a"), registry.servers.value.map { it.id })
    }

    @Test
    fun `reorderServers appends missing ids in original order`() = runTest {
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
                ServerConfig("c", "C", "10.0.0.3:4800"),
            ),
        )
        val registry = startedRegistry(store)

        registry.reorderServers(listOf("c"))

        assertEquals(listOf("c", "a", "b"), registry.servers.value.map { it.id })
    }

    @Test
    fun `reorderServers republishes nodes in new order`() = runTest {
        val clientA = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n-a", "bot-a")))
            channelsResult = Result.success(listOf(ChannelInfo("c-a", "ch-a")))
        }
        val clientB = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n-b", "bot-b")))
            channelsResult = Result.success(listOf(ChannelInfo("c-b", "ch-b")))
        }
        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("a", "A", "10.0.0.1:4800"),
                ServerConfig("b", "B", "10.0.0.2:4800"),
            ),
        )
        val factory = FakeNerveClientFactory(
            mutableMapOf(
                "a" to ArrayDeque(listOf(clientA)),
                "b" to ArrayDeque(listOf(clientB)),
            ),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        registry.start(ClientRegistration(name = "android-ui"))

        registry.reorderServers(listOf("b", "a"))

        assertEquals(listOf("b:n-b", "a:n-a"), registry.nodes.value.map { "${it.serverId}:${it.node.id}" })
    }

    private suspend fun startedRegistry(store: FakeServerConfigStore): RealServerRegistry {
        val factory = FakeNerveClientFactory(
            store.load().associate { config ->
                config.id to ArrayDeque(listOf(FakeNerveClient()))
            }.toMutableMap(),
        )
        val registry = RealServerRegistry(store, factory, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        registry.start(ClientRegistration(name = "android-ui"))
        return registry
    }
}
