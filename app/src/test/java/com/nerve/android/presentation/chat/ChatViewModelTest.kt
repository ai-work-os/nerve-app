package com.nerve.android.presentation.chat

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.dm.DmAction
import com.nerve.android.domain.dm.DmEventMapper
import com.nerve.android.domain.dm.DmKey
import com.nerve.android.domain.dm.DmMappedEvent
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.domain.dm.DmSessionManager
import com.nerve.android.domain.server.FakeNerveClient
import com.nerve.android.domain.server.ServerScopedEvent
import com.nerve.android.presentation.FakeServerRegistry
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.RpcException
import com.nerve.android.transport.SnapshotAction
import com.nerve.android.transport.SnapshotMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatViewModelTest {

    private fun nodeUpdateEvent(
        nodeId: String,
        nodeName: String,
        sessionUpdate: String,
        text: String? = null,
        toolCallId: String? = null,
        toolName: String? = null,
        toolInput: String? = null,
    ): NerveEvent.NodeUpdate = NerveEvent.NodeUpdate(
        nodeId = nodeId,
        name = nodeName,
        detail = buildJsonObject {
            put("update", buildJsonObject {
                put("sessionUpdate", sessionUpdate)
                if (text != null) {
                    put("content", buildJsonObject { put("text", text) })
                }
                if (toolCallId != null) {
                    put("toolCallId", toolCallId)
                    if (toolName != null) {
                        put("_meta", buildJsonObject {
                            put("claudeCode", buildJsonObject { put("toolName", toolName) })
                        })
                    }
                    put("rawInput", toolInput ?: "")
                }
            })
        },
    )

    private fun idleEvent(nodeId: String): NerveEvent.NodeStatusChanged =
        NerveEvent.NodeStatusChanged(
            nodeId = nodeId,
            status = "idle",
            detail = buildJsonObject {},
        )

    private fun scoped(serverId: String, event: NerveEvent) =
        ServerScopedEvent(serverId, event)

    /** Pre-fill a finalized assistant message into sessionManager via events. */
    private fun prefillAssistant(mgr: DmSessionManager, nodeId: String, nodeName: String, content: String, ts: Long) {
        mgr.onEvent(DmMappedEvent.AgentMessageStart(nodeId = nodeId, nodeName = nodeName, timestamp = ts, messageId = "pre-$ts"))
        mgr.onEvent(DmMappedEvent.AgentMessageChunk(nodeId = nodeId, text = content, timestamp = ts + 1))
        mgr.onEvent(DmMappedEvent.AgentMessageEnd(nodeId = nodeId, nodeName = nodeName, timestamp = ts + 2, fallbackText = null))
    }

    /** Pre-fill a finalized user message into sessionManager via events. */
    private fun prefillUser(mgr: DmSessionManager, nodeId: String, nodeName: String, content: String, ts: Long) {
        mgr.onEvent(DmMappedEvent.UserMessage(nodeId = nodeId, nodeName = nodeName, content = content, timestamp = ts, messageId = "user-$ts"))
    }

    @Test
    fun `enter dm reads store subscribes sends prompt and tracks streaming`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        prefillUser(sessionManager, "n1", "bot", "hello", 100L)
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        assertEquals(listOf("hello"), vm.uiState.value.messages.map { it.content })
        assertEquals(listOf("n1"), client.subscribeCalls)

        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_chunk", text = "hi")),
        )
        assertTrue(vm.uiState.value.isStreaming)

        registry.events.emit(scoped("s1", idleEvent("n1")))
        assertFalse(vm.uiState.value.isStreaming)

        vm.sendMessage("ping")
        assertEquals(listOf("n1" to "ping"), client.promptCalls)
    }

    @Test
    fun `switching dm unsubscribes old and binds new key`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        prefillUser(sessionManager, "n1", "bot-1", "one", 100L)
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot-1")
        vm.enterDm("s1", "n2", "bot-2")

        assertEquals(listOf("n1"), client.unsubscribeCalls)
        assertEquals(listOf("n1", "n2"), client.subscribeCalls)
    }

    @Test
    fun `leave dm unsubscribes and resets session`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot-1")
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot-1", "agent_message_chunk", text = "hello")),
        )
        assertTrue(vm.uiState.value.isStreaming)

        vm.leaveDm()

        assertEquals(listOf("n1"), client.unsubscribeCalls)
        assertFalse(vm.uiState.value.isStreaming)
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun `enter dm subscribe failure is shown without throwing`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        client.subscribeResult = Result.failure(RpcException.ServerError(-32000, "node not found"))
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "missing", "bot")

        assertEquals("node not found", vm.uiState.value.errorMessage)
        assertEquals("missing", vm.uiState.value.nodeId)
    }

    @Test
    fun `leave dm unsubscribe failure does not throw and still resets`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        client.unsubscribeResult = Result.failure(RpcException.TransportDisconnected())
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.leaveDm()

        assertNull(vm.uiState.value.nodeId)
        assertEquals(listOf("n1"), client.unsubscribeCalls)
    }

    @Test
    fun `onCleared stops dm collect and attach`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        val dispatcher = StandardTestDispatcher(testScheduler)
        registry.clients["s1"] = client
        prefillUser(sessionManager, "n1", "bot-1", "one", 100L)
        val vm = ChatViewModel(sessionManager, mapper, registry, dispatcher)

        vm.enterDm("s1", "n1", "bot-1")
        advanceUntilIdle()
        clearViewModel(vm)
        advanceUntilIdle()

        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot-1", "agent_message_chunk", text = "late")),
        )
        advanceUntilIdle()

        assertEquals(listOf("one"), vm.uiState.value.messages.map { it.content })
        assertFalse(vm.uiState.value.isStreaming)
    }

    @Test
    fun `sendMessage adds local user message to uiState`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.sendMessage("hello")

        val messages = vm.uiState.value.messages
        assertTrue(messages.any { it.role == DmRole.USER && it.content == "hello" },
            "Expected a USER message with content 'hello' in uiState.messages but got: $messages")
    }

    @Test
    fun `sendMessage user message appears before server response`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.sendMessage("ping")

        val userMessages = vm.uiState.value.messages.filter { it.role == DmRole.USER }
        assertEquals(1, userMessages.size,
            "Expected exactly 1 user message after sendMessage, got ${userMessages.size}: $userMessages")
        assertEquals("ping", userMessages.first().content)
    }

    @Test
    fun `sendImage sends prompt with image attachment and local user message`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.sendImage("see this", "image/png", "abc123")

        assertEquals(listOf("n1" to "see this"), client.promptCalls)
        assertEquals(listOf("image/png:abc123"), client.imagePromptCalls)
        assertTrue(vm.uiState.value.messages.any { it.role == DmRole.USER && it.content == "see this\n[image:image/png]" })
    }

    @Test
    fun `thinking chunk sets isStreaming true`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        assertFalse(vm.uiState.value.isStreaming)

        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_thought_chunk", text = "hmm")),
        )
        assertTrue(vm.uiState.value.isStreaming,
            "isStreaming should be true after agent_thought_chunk")
    }

    @Test
    fun `thinking then text then end cycles streaming correctly`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        // thought chunk → streaming on
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_thought_chunk", text = "thinking")),
        )
        assertTrue(vm.uiState.value.isStreaming)

        // text chunk → still streaming
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_chunk", text = "answer")),
        )
        assertTrue(vm.uiState.value.isStreaming)

        // end → streaming off
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_end")),
        )
        assertFalse(vm.uiState.value.isStreaming,
            "isStreaming should be false after agent_message_end")
    }

    @Test
    fun `node idle resets isStreaming after thought and tool_call`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        // thought chunk → streaming on
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_thought_chunk", text = "let me think")),
        )
        assertTrue(vm.uiState.value.isStreaming, "isStreaming should be true after thought chunk")

        // tool_call arrives
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "tool_call", toolCallId = "1", toolName = "bash", toolInput = "ls")),
        )

        // node idle → must reset isStreaming regardless
        registry.events.emit(scoped("s1", idleEvent("n1")))
        assertFalse(vm.uiState.value.isStreaming,
            "node idle must reset isStreaming even without agent_message_end")
    }

    @Test
    fun `node spawned from current dm adds persistent open dm action`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "parent-1", "main")
        registry.events.emit(
            scoped(
                "s1",
                NerveEvent.NodeSpawned(
                    nodeId = "child-1",
                    name = "worker",
                    adapter = "codex",
                    spawnedByNodeId = "parent-1",
                    spawnedByNodeName = "main",
                    channelId = "ch-1",
                ),
            ),
        )

        val actionMessage = vm.uiState.value.messages.single { it.role == DmRole.SYSTEM }
        assertEquals("已创建 worker", actionMessage.content)
        assertEquals("child-1", actionMessage.action?.nodeId)
        assertEquals("worker", actionMessage.action?.nodeName)
        assertEquals("s1", actionMessage.action?.serverId)
    }

    @Test
    fun `spawned action remains after stream ends`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "parent-1", "main")
        registry.events.emit(scoped("s1", nodeUpdateEvent("parent-1", "main", "agent_message_chunk", text = "creating")))
        registry.events.emit(
            scoped(
                "s1",
                NerveEvent.NodeSpawned(
                    nodeId = "child-1",
                    name = "worker",
                    adapter = "codex",
                    spawnedByNodeId = "parent-1",
                    spawnedByNodeName = "main",
                    channelId = null,
                ),
            ),
        )
        registry.events.emit(scoped("s1", nodeUpdateEvent("parent-1", "main", "agent_message_end")))

        assertTrue(vm.uiState.value.messages.any { it.action?.nodeId == "child-1" })
        assertTrue(vm.uiState.value.messages.any { it.role == DmRole.ASSISTANT && it.content == "creating" })
    }

    // === New cases ===

    @Test
    fun `streamingMessage in uiState reflects DmSessionManager streaming`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        // chunk → streamingMessage non-null with content
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_chunk", text = "hello")),
        )
        val streaming = vm.uiState.value.streamingMessage
        assertNotNull(streaming, "streamingMessage should be non-null after chunk")
        assertTrue(streaming.content.contains("hello"))

        // end → streamingMessage null
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_end")),
        )
        assertNull(vm.uiState.value.streamingMessage,
            "streamingMessage should be null after end")
    }

    @Test
    fun `isStreaming derived from streamingMessage not null`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        // initial: no streaming
        assertNull(vm.uiState.value.streamingMessage)
        assertFalse(vm.uiState.value.isStreaming)

        // start streaming
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_chunk", text = "x")),
        )
        assertEquals(vm.uiState.value.streamingMessage != null, vm.uiState.value.isStreaming,
            "isStreaming should equal (streamingMessage != null) during streaming")

        // end streaming
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_end")),
        )
        assertEquals(vm.uiState.value.streamingMessage != null, vm.uiState.value.isStreaming,
            "isStreaming should equal (streamingMessage != null) after end")
    }

    @Test
    fun `enterDm creates fresh session and old messages gone`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        // enter first DM, receive a message
        vm.enterDm("s1", "n1", "bot-1")
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot-1", "agent_message_chunk", text = "old msg")),
        )
        registry.events.emit(scoped("s1", idleEvent("n1")))
        assertTrue(vm.uiState.value.messages.isNotEmpty(), "Should have messages after first DM")

        // switch to second DM
        vm.enterDm("s1", "n2", "bot-2")

        // old messages should not be in uiState
        val contents = vm.uiState.value.messages.map { it.content }
        assertFalse(contents.any { it.contains("old msg") },
            "Old DM messages should not appear after switching, got: $contents")
    }

    // === message_snapshot (subscribe/resubscribe) ===

    private fun snapshotEvent(
        nodeId: String,
        nodeName: String,
        messages: List<Triple<String, String, String>>, // (role, text, id)
    ): NerveEvent.MessageSnapshot = NerveEvent.MessageSnapshot(
        nodeId = nodeId,
        name = nodeName,
        messages = messages.mapIndexed { i, (role, text, id) ->
            SnapshotMessage(
                id = id,
                nodeId = nodeId,
                role = role,
                sender = if (role == "user") "renjinxi" else nodeName,
                text = text,
                ts = (1000.0 + i),
            )
        },
    )

    @Test
    fun `message_snapshot replaces uiState messages`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")

        // pre-fill with a stale local user message
        vm.sendMessage("stale")
        assertTrue(vm.uiState.value.messages.any { it.content == "stale" })

        // server sends snapshot with authoritative history
        registry.events.emit(
            scoped("s1", snapshotEvent("n1", "bot", listOf(
                Triple("user", "real question", "u1"),
                Triple("agent", "real answer", "a1"),
            ))),
        )

        val msgs = vm.uiState.value.messages
        assertEquals(2, msgs.size)
        assertEquals("real question", msgs[0].content)
        assertEquals(DmRole.USER, msgs[0].role)
        assertEquals("real answer", msgs[1].content)
        assertEquals(DmRole.ASSISTANT, msgs[1].role)
        // stale message is gone
        assertFalse(msgs.any { it.content == "stale" })
    }

    @Test
    fun `message_snapshot empty clears uiState messages`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.sendMessage("to-be-cleared")
        assertEquals(1, vm.uiState.value.messages.size)

        registry.events.emit(
            scoped("s1", snapshotEvent("n1", "bot", emptyList())),
        )

        assertEquals(0, vm.uiState.value.messages.size)
    }

    @Test
    fun `message_snapshot ignored when nodeId does not match`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "n1", "bot")
        vm.sendMessage("keep me")

        // snapshot for a different node
        registry.events.emit(
            scoped("s1", snapshotEvent("other-node", "other", listOf(
                Triple("agent", "wrong context", "x1"),
            ))),
        )

        // current DM's messages should be untouched
        assertTrue(vm.uiState.value.messages.any { it.content == "keep me" })
        assertFalse(vm.uiState.value.messages.any { it.content == "wrong context" })
    }

    @Test
    fun `message_snapshot restores open dm action`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)

        vm.enterDm("s1", "parent-1", "parent")

        registry.events.emit(
            scoped(
                "s1",
                NerveEvent.MessageSnapshot(
                    nodeId = "parent-1",
                    name = "parent",
                    messages = listOf(
                        SnapshotMessage(
                            id = "spawn-child-1",
                            nodeId = "parent-1",
                            role = "system",
                            sender = "parent",
                            text = "已创建 worker",
                            ts = 1710000000000.0,
                            action = SnapshotAction(
                                type = "open_dm",
                                nodeId = "child-1",
                                nodeName = "worker",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val action = assertIs<DmAction.OpenDm>(vm.uiState.value.messages.single().action)
        assertEquals("s1", action.serverId)
        assertEquals("child-1", action.nodeId)
        assertEquals("worker", action.nodeName)
    }

    // === Replay via live events (legacy path) ===

    @Test
    fun `enterDm batches replay events and flushes after subscribe`() = runTest {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = FakeServerRegistry()
        val client = FakeNerveClient()
        var subscribedSignal = false
        registry.clients["s1"] = client
        val vm = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined,
            onSubscribed = { _, _ -> subscribedSignal = true })

        // Pre-load replay events that will come through during subscribe
        // Simulate: subscribe triggers replay of 2 messages
        vm.enterDm("s1", "n1", "bot")

        // After enterDm, subscribe has completed and batch should be flushed
        assertTrue(subscribedSignal, "onSubscribed should have been called")

        // Feed replay-style events (end with fallback, no active stream)
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "user_message", text = "q1")),
        )
        registry.events.emit(
            scoped("s1", nodeUpdateEvent("n1", "bot", "agent_message_end", text = "a1")),
        )

        // These come after batch ended, so they should appear immediately
        val msgs = vm.uiState.value.messages
        assertEquals(2, msgs.size, "Messages should appear after subscribe replay: $msgs")
    }

    private fun clearViewModel(viewModel: ViewModel) {
        val method = ViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }
}
