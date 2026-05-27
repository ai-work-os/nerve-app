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
import com.nerve.android.transport.PromptAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
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
        assertEquals(listOf(FakeNerveClient.PromptCall("n1", "ping")), client.promptCalls)
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

    @Test
    fun `enterDm calls session list to check for history`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        val sessionListCalls = client.rpcCalls.filter { it.first == "session.list" }
        assertTrue(sessionListCalls.isNotEmpty(),
            "Expected enterDm to call session.list RPC, but rpcCalls were: ${client.rpcCalls.map { it.first }}")
    }

    @Test
    fun `enterDm loads history when session exists`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        // session.list returns one session
        client.callResults["session.list"] = Result.success(buildJsonObject {
            put("sessions", buildJsonArray {
                addJsonObject { put("sessionId", "sess-1") }
            })
        })
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        val sessionLoadCalls = client.rpcCalls.filter { it.first == "session.load" }
        assertTrue(sessionLoadCalls.isNotEmpty(),
            "Expected enterDm to call session.load for existing session, but rpcCalls were: ${client.rpcCalls.map { it.first }}")
    }

    @Test
    fun `sendMessage adds local user message to uiState`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.sendMessage("hello")

        val messages = vm.uiState.value.messages
        assertTrue(messages.any { it.role == DmRole.USER && it.content == "hello" },
            "Expected a USER message with content 'hello' in uiState.messages but got: $messages")
    }

    @Test
    fun `sendMessage user message appears before server response`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.sendMessage("ping")

        val userMessages = vm.uiState.value.messages.filter { it.role == DmRole.USER }
        assertEquals(1, userMessages.size,
            "Expected exactly 1 user message after sendMessage, got ${userMessages.size}: $userMessages")
        assertEquals("ping", userMessages.first().content)
    }

    @Test
    fun `sendMessage forwards multiple image attachments`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)
        val attachments = listOf(
            PromptAttachment.Image(mimeType = "image/png", data = "one"),
            PromptAttachment.Image(mimeType = "image/jpeg", data = "two"),
        )

        vm.enterDm("s1", "n1", "bot")
        vm.sendMessage("look", attachments)

        assertEquals(listOf(FakeNerveClient.PromptCall("n1", "look", attachments)), client.promptCalls)
    }

    @Test
    fun `sendMessage ignores prompts while streaming`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject { put("sessionUpdate", "agent_message_chunk") })
                    },
                ),
            ),
        )
        vm.sendMessage("blocked", listOf(PromptAttachment.Image(mimeType = "image/png", data = "one")))

        assertTrue(client.promptCalls.isEmpty())
        assertTrue(vm.uiState.value.messages.none { it.content == "blocked" })
    }

    @Test
    fun `thinking chunk sets isStreaming true`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        assertFalse(vm.uiState.value.isStreaming)

        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_thought_chunk")
                        })
                    },
                ),
            ),
        )
        assertTrue(vm.uiState.value.isStreaming,
            "isStreaming should be true after agent_thought_chunk")
    }

    @Test
    fun `thinking then text then end cycles streaming correctly`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        // thought chunk → streaming on
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_thought_chunk")
                        })
                    },
                ),
            ),
        )
        assertTrue(vm.uiState.value.isStreaming)

        // text chunk → still streaming
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_message_chunk")
                        })
                    },
                ),
            ),
        )
        assertTrue(vm.uiState.value.isStreaming)

        // end → streaming off
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_message_end")
                        })
                    },
                ),
            ),
        )
        assertFalse(vm.uiState.value.isStreaming,
            "isStreaming should be false after agent_message_end")
    }

    @Test
    fun `node idle resets isStreaming after thought and tool_call`() = runTest {
        val store = InMemoryDmStore()
        val processor = FakeDmEventProcessor()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(store, processor, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        // thought chunk → streaming on
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_thought_chunk")
                        })
                    },
                ),
            ),
        )
        assertTrue(vm.uiState.value.isStreaming, "isStreaming should be true after thought chunk")

        // tool_call arrives — streaming should stay true (tool executing)
        registry.events.emit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "tool_call")
                        })
                    },
                ),
            ),
        )
        // No agent_message_end — simulate lost end event

        // node idle → must reset isStreaming regardless
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
        assertFalse(vm.uiState.value.isStreaming,
            "node idle must reset isStreaming even without agent_message_end")
    }

    private fun clearViewModel(viewModel: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
