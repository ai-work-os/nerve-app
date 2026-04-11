package com.nerve.android.presentation.chat

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.dm.DmKey
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.domain.dm.InMemoryDmStore
import com.nerve.android.domain.server.FakeNerveClient
import com.nerve.android.domain.server.ServerScopedEvent
import com.nerve.android.presentation.FakeDmEventProcessor
import com.nerve.android.presentation.FakeServerRegistry
import com.nerve.android.transport.NerveEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatViewModelTest {
    @Test
    fun `enter dm reads store subscribes sends prompt and tracks streaming`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        store.appendMessage(
            DmKey("s1:n1"),
            DmMessage("m1", DmRole.USER, "hello", 100L, "n1", "bot"),
        )
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        assertEquals(listOf("hello"), vm.uiState.value.messages.map { it.content })
        assertEquals(listOf("n1"), client.subscribeCalls)
        assertEquals(listOf("s1" to "n1"), processor.attachCalls)

        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put(
                            "update",
                            buildJsonObject {
                                put("sessionUpdate", "agent_message_chunk")
                            },
                        )
                    },
                ),
            ),
        )
        assertTrue(vm.uiState.value.isStreaming)

        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeStatusChanged(
                    nodeId = "n1",
                    status = "idle",
                    detail = buildJsonObject {},
                ),
            ),
        )
        assertFalse(vm.uiState.value.isStreaming)

        vm.sendMessage("ping")
        assertEquals(listOf("n1" to "ping"), client.promptCalls)
    }

    @Test
    fun `switching dm unsubscribes old and binds new key`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        store.appendMessage(DmKey("s1:n1"), DmMessage("m1", DmRole.USER, "one", 100L, "n1", "bot-1"))
        store.appendMessage(DmKey("s1:n2"), DmMessage("m2", DmRole.USER, "two", 200L, "n2", "bot-2"))
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot-1")
        vm.enterDm("s1", "n2", "bot-2")

        assertEquals(listOf("n1"), client.unsubscribeCalls)
        assertEquals(listOf("n1", "n2"), client.subscribeCalls)
        assertEquals(listOf("two"), vm.uiState.value.messages.map { it.content })
    }

    @Test
    fun `leave dm unsubscribes and resets streaming without clearing store`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        store.appendMessage(DmKey("s1:n1"), DmMessage("m1", DmRole.USER, "one", 100L, "n1", "bot-1"))
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot-1")
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot-1",
                    detail = buildJsonObject {
                        put("update", buildJsonObject { put("sessionUpdate", "agent_message_chunk") })
                    },
                ),
            ),
        )
        assertTrue(vm.uiState.value.isStreaming)

        vm.leaveDm()

        assertEquals(listOf("n1"), client.unsubscribeCalls)
        assertFalse(vm.uiState.value.isStreaming)
        assertEquals(listOf("one"), store.messages(DmKey("s1:n1")).value.map { it.content })
    }

    @Test
    fun `onCleared stops dm collect and attach`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        val dispatcher = StandardTestDispatcher(testScheduler)
        registry.clients["s1"] = client
        store.appendMessage(DmKey("s1:n1"), DmMessage("m1", DmRole.USER, "one", 100L, "n1", "bot-1"))
        val vm = ChatViewModel(store, processor, registry, dispatcher)

        vm.enterDm("s1", "n1", "bot-1")
        advanceUntilIdle()
        clearViewModel(vm)
        advanceUntilIdle()

        store.appendMessage(DmKey("s1:n1"), DmMessage("m2", DmRole.USER, "two", 200L, "n1", "bot-1"))
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot-1",
                    detail = buildJsonObject {
                        put("update", buildJsonObject { put("sessionUpdate", "agent_message_chunk") })
                    },
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("one"), vm.uiState.value.messages.map { it.content })
        assertFalse(vm.uiState.value.isStreaming)
        assertEquals(listOf("s1" to "n1"), processor.cancelledCalls)
    }

    private fun clearViewModel(viewModel: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
