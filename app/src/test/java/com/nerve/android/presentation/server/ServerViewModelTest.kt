package com.nerve.android.presentation.server

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.server.FakeServerRegistry
import com.nerve.android.domain.server.ServerConnection
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerViewModelTest {
    @Test
    fun `server ui follows registry and delegates add remove`() = runTest {
        val registry = FakeServerRegistry()
        registry.servers.value = listOf(ServerConfig("s1", "Home", "10.0.0.1:4800"))
        registry.connections.value = listOf(ServerConnection("s1", "Home", ConnectionState.CONNECTED))
        val vm = ServerViewModel(registry, Dispatchers.Unconfined)

        assertEquals(listOf("s1"), vm.uiState.value.servers.map { it.id })
        assertEquals(listOf(ConnectionState.CONNECTED), vm.uiState.value.connections.map { it.state })

        vm.addServer("s2", "Lab", "10.0.0.2:4800")
        vm.removeServer("s1")

        assertEquals(listOf(ServerConfig("s2", "Lab", "10.0.0.2:4800")), registry.addServerCalls)
        assertEquals(listOf("s1"), registry.removeServerCalls)
    }

    @Test
    fun `server ui follows later registry updates`() = runTest {
        val registry = FakeServerRegistry()
        val vm = ServerViewModel(registry, Dispatchers.Unconfined)

        registry.servers.value = listOf(ServerConfig("s2", "Lab", "10.0.0.2:4800"))
        registry.connections.value = listOf(ServerConnection("s2", "Lab", ConnectionState.RECONNECTING))

        assertEquals(listOf("s2"), vm.uiState.value.servers.map { it.id })
        assertEquals(listOf(ConnectionState.RECONNECTING), vm.uiState.value.connections.map { it.state })
    }

    @Test
    fun `onCleared stops server collect`() = runTest {
        val registry = FakeServerRegistry()
        val dispatcher = StandardTestDispatcher(testScheduler)
        registry.servers.value = listOf(ServerConfig("s1", "Home", "10.0.0.1:4800"))
        registry.connections.value = listOf(ServerConnection("s1", "Home", ConnectionState.CONNECTED))
        val vm = ServerViewModel(registry, dispatcher)
        advanceUntilIdle()
        clearViewModel(vm)
        advanceUntilIdle()

        registry.servers.value = listOf(ServerConfig("s2", "Lab", "10.0.0.2:4800"))
        registry.connections.value = listOf(ServerConnection("s2", "Lab", ConnectionState.RECONNECTING))
        advanceUntilIdle()

        assertEquals(listOf("s1"), vm.uiState.value.servers.map { it.id })
        assertEquals(listOf(ConnectionState.CONNECTED), vm.uiState.value.connections.map { it.state })
    }

    private fun clearViewModel(viewModel: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
