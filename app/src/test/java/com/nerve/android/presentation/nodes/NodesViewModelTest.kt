package com.nerve.android.presentation.nodes

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.server.FakeNerveClient
import com.nerve.android.domain.server.FakeServerRegistry
import com.nerve.android.domain.server.ServerConnection
import com.nerve.android.domain.server.ServerNode
import com.nerve.android.domain.server.ServerScopedEvent
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.NodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NodesViewModelTest {
    @Test
    fun `nodes ui follows registry status map and spawn stop refresh target server`() = runTest {
        val registry = FakeServerRegistry()
        val clientA = FakeNerveClient()
        val clientB = FakeNerveClient()
        registry.clients["s1"] = clientA
        registry.clients["s2"] = clientB
        registry.nodes.value = listOf(
            ServerNode("s1", "Home", NodeInfo("n1", "bot")),
            ServerNode("s2", "Lab", NodeInfo("n1", "bot")),
        )
        val vm = NodesViewModel(registry, Dispatchers.Unconfined)

        registry.events.emit(ServerScopedEvent("s1", NerveEvent.NodeStatusChanged("n1", "busy", buildJsonObject {})))
        registry.events.emit(ServerScopedEvent("s2", NerveEvent.NodeStopped("n1")))

        assertEquals(listOf("busy", "stopped"), vm.uiState.value.items.map { it.status })

        vm.spawnNode("s1", "claude", "alpha", "/tmp")
        vm.stopNode("s2", "n1")

        assertEquals(listOf(Triple("claude", "alpha" as String?, "/tmp" as String?)), clientA.spawnCalls)
        assertEquals(listOf("n1"), clientB.stopCalls)
        assertEquals(listOf<String?>("s1", "s2"), registry.refreshCalls)
    }

    @Test
    fun `onCleared stops nodes collect`() = runTest {
        val registry = FakeServerRegistry()
        val dispatcher = StandardTestDispatcher(testScheduler)
        registry.nodes.value = listOf(ServerNode("s1", "Home", NodeInfo("n1", "bot")))
        val vm = NodesViewModel(registry, dispatcher)
        advanceUntilIdle()
        clearViewModel(vm)
        advanceUntilIdle()

        registry.nodes.value = listOf(ServerNode("s1", "Home", NodeInfo("n2", "bot-2")))
        registry.events.emit(ServerScopedEvent("s1", NerveEvent.NodeStatusChanged("n2", "busy", buildJsonObject {})))
        advanceUntilIdle()

        assertEquals(listOf("n1"), vm.uiState.value.items.map { it.nodeId })
        assertEquals(listOf("idle"), vm.uiState.value.items.map { it.status })
    }

    @Test
    fun `node items include adapter cwd and short id`() = runTest {
        val registry = FakeServerRegistry()
        registry.nodes.value = listOf(
            ServerNode("s1", "Home", NodeInfo("abc12345-long-id", "bot", adapter = "claude", cwd = "/home/user/project")),
        )
        val vm = NodesViewModel(registry, Dispatchers.Unconfined)
        val item = vm.uiState.value.items.first()

        assertEquals("claude", item.adapter)
        assertEquals("/home/user/project", item.cwd)
        assertEquals("abc12345", item.shortId)
        assertEquals("project", item.cwdLabel)
    }

    @Test
    fun `cwdLabel falls back to adapter when cwd is null`() = runTest {
        val registry = FakeServerRegistry()
        registry.nodes.value = listOf(
            ServerNode("s1", "Home", NodeInfo("n1", "bot", adapter = "claude")),
        )
        val vm = NodesViewModel(registry, Dispatchers.Unconfined)
        val item = vm.uiState.value.items.first()

        assertEquals("claude", item.cwdLabel)
    }

    @Test
    fun `connection counts track server connections`() = runTest {
        val registry = FakeServerRegistry()
        registry.servers.value = listOf(
            ServerConfig("s1", "Home", "h:1"),
            ServerConfig("s2", "Lab", "h:2"),
        )
        registry.connections.value = listOf(
            ServerConnection("s1", "Home", ConnectionState.CONNECTED),
            ServerConnection("s2", "Lab", ConnectionState.DISCONNECTED),
        )
        val vm = NodesViewModel(registry, Dispatchers.Unconfined)

        assertEquals(1, vm.uiState.value.connectedCount)
        assertEquals(2, vm.uiState.value.totalCount)

        registry.connections.value = listOf(
            ServerConnection("s1", "Home", ConnectionState.CONNECTED),
            ServerConnection("s2", "Lab", ConnectionState.CONNECTED),
        )
        assertEquals(2, vm.uiState.value.connectedCount)
        assertEquals(2, vm.uiState.value.totalCount)
    }

    private fun clearViewModel(viewModel: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
