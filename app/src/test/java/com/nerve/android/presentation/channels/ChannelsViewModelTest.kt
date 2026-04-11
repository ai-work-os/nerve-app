package com.nerve.android.presentation.channels

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.channel.ChannelKey
import com.nerve.android.domain.channel.ChannelMessage
import com.nerve.android.domain.channel.ChannelMeta
import com.nerve.android.domain.channel.InMemoryChannelStore
import com.nerve.android.domain.server.FakeNerveClient
import com.nerve.android.domain.server.ServerChannel
import com.nerve.android.domain.server.ServerScopedEvent
import com.nerve.android.presentation.FakeChannelEventProcessor
import com.nerve.android.presentation.FakeServerRegistry
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.model.ChannelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ChannelsViewModelTest {
    @Test
    fun `enter attaches once per server reads store and join post refreshes on channel events`() = runTest {
        val store = InMemoryChannelStore()
        val processor = FakeChannelEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        registry.channels.value = listOf(
            ServerChannel("s1", "Home", ChannelInfo("c1", "general")),
            ServerChannel("s1", "Home", ChannelInfo("c2", "random")),
        )
        store.appendMessage(ChannelKey("s1:c1"), ChannelMessage("m1", "c1", "alice", "hello", 100L))
        store.upsertMeta(ChannelKey("s1:c1"), ChannelMeta("c1", "general", isClosed = false))
        store.appendMessage(ChannelKey("s1:c2"), ChannelMessage("m2", "c2", "bob", "yo", 200L))
        store.upsertMeta(ChannelKey("s1:c2"), ChannelMeta("c2", "random", isClosed = false))
        val vm = ChannelsViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterChannel("s1", "c1", "general")
        assertEquals("s1", vm.uiState.value.currentServerId)
        assertEquals("c1", vm.uiState.value.currentChannelId)
        assertEquals("general", vm.uiState.value.currentChannelName)
        assertEquals(listOf("hello"), vm.uiState.value.messages.map { it.content })
        assertEquals("general", vm.uiState.value.currentMeta?.name)

        vm.enterChannel("s1", "c2", "random")
        assertEquals("c2", vm.uiState.value.currentChannelId)
        assertEquals("random", vm.uiState.value.currentChannelName)
        assertEquals(listOf("yo"), vm.uiState.value.messages.map { it.content })
        assertEquals("random", vm.uiState.value.currentMeta?.name)
        assertEquals(listOf("s1"), processor.attachCalls)

        vm.joinChannel("s1", "c1")
        vm.sendMessage("ping")
        assertEquals("channel.join", client.rpcCalls[0].first)
        assertEquals("c1", client.rpcCalls[0].second["channelId"]!!.jsonPrimitive.content)
        assertEquals("channel.post", client.rpcCalls[1].first)
        assertEquals("ping", client.rpcCalls[1].second["content"]!!.jsonPrimitive.content)

        registry.events.emit(ServerScopedEvent("s1", NerveEvent.ChannelCreated("c3", "new")))
        registry.events.emit(ServerScopedEvent("s1", NerveEvent.ChannelClosed("c1", "general")))

        assertEquals(listOf("s1", "s1"), registry.refreshCalls.takeLast(2))
    }

    @Test
    fun `onCleared stops channel collect and attach`() = runTest {
        val store = InMemoryChannelStore()
        val processor = FakeChannelEventProcessor()
        val registry = FakeServerRegistry()
        val dispatcher = StandardTestDispatcher(testScheduler)
        store.appendMessage(ChannelKey("s1:c1"), ChannelMessage("m1", "c1", "alice", "hello", 100L))
        store.upsertMeta(ChannelKey("s1:c1"), ChannelMeta("c1", "general", isClosed = false))
        val vm = ChannelsViewModel(store, processor, registry, dispatcher)

        vm.enterChannel("s1", "c1", "general")
        advanceUntilIdle()
        clearViewModel(vm)
        advanceUntilIdle()

        store.appendMessage(ChannelKey("s1:c1"), ChannelMessage("m2", "c1", "bob", "later", 200L))
        store.upsertMeta(ChannelKey("s1:c1"), ChannelMeta("c1", "changed", isClosed = true))
        registry.events.emit(ServerScopedEvent("s1", NerveEvent.ChannelClosed("c1", "general")))
        advanceUntilIdle()

        assertEquals(listOf("hello"), vm.uiState.value.messages.map { it.content })
        assertEquals("general", vm.uiState.value.currentMeta?.name)
        assertEquals(false, vm.uiState.value.currentMeta?.isClosed)
        assertEquals(listOf("s1"), processor.cancelledCalls)
        assertEquals(emptyList<String?>(), registry.refreshCalls)
    }

    private fun clearViewModel(viewModel: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
