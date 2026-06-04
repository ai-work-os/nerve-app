package com.nerve.android.ui.chat

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nerve.android.MainActivity
import com.nerve.android.NerveApp
import com.nerve.android.domain.dm.DmEventMapper
import com.nerve.android.domain.dm.DmMappedEvent
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.domain.dm.DmSessionManager
import com.nerve.android.domain.server.ServerChannel
import com.nerve.android.domain.server.ServerConnection
import com.nerve.android.domain.server.ServerNode
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.domain.server.ServerScopedEvent
import com.nerve.android.presentation.chat.ChatUiState
import com.nerve.android.presentation.chat.ChatViewModel
import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveClient
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import com.nerve.android.transport.model.PromptResult
import com.nerve.android.transport.model.SpawnResult
import com.nerve.android.ui.theme.WhiteSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun chatScreen_renders_messages_typing_error_and_send_input() {
        var sentText: String? = null
        var cancelCalls = 0
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    serverId = "s1",
                    nodeId = "n1",
                    nodeName = "bot",
                    messages = listOf(
                        DmMessage("u1", DmRole.USER, "hello", 100L, "n1", "bot"),
                        DmMessage("a1", DmRole.ASSISTANT, "world", 200L, "n1", "bot"),
                    ),
                    isStreaming = true,
                    errorMessage = "boom",
                ),
                streamingText = "partial reply",
                onSend = { text, _ -> sentText = text },
                onPickImage = {},
                onCancel = { cancelCalls += 1 },
            )
        }

        composeRule.onNodeWithText("hello").assertIsDisplayed()
        composeRule.onNodeWithText("world").assertIsDisplayed()
        composeRule.onNodeWithText("partial reply").assertIsDisplayed()
        composeRule.onNodeWithTag("streaming-bubble-assistant").assertIsDisplayed()
        composeRule.onNodeWithText("boom").assertIsDisplayed()
        composeRule.onNodeWithText("bot").assertIsDisplayed()
        composeRule.onNodeWithText("Thinking").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Message").performTextInput("ping")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Send").performClick()
        composeRule.onNodeWithText("Message").assertTextEquals("")
        composeRule.onAllNodesWithText("ping").assertCountEquals(0)
        check(sentText == "ping")
        check(cancelCalls == 1)
    }

    @Test
    fun chatScreen_send_disabled_for_blank_and_sending() {
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    serverId = "s1",
                    nodeId = "n1",
                    nodeName = "bot",
                    isSending = true,
                ),
                streamingText = "",
                onSend = { _, _ -> },
                onPickImage = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Send").assertIsNotEnabled()
        composeRule.onNodeWithText("Sending").assertIsDisplayed()
    }

    @Test
    fun chatScreen_keepsLastMessageAboveFloatingInputWithAttachments() {
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    serverId = "s1",
                    nodeId = "n1",
                    nodeName = "bot",
                    messages = listOf(
                        DmMessage("m1", DmRole.ASSISTANT, "older message", 100L, "n1", "bot"),
                        DmMessage("m2", DmRole.ASSISTANT, "bottom-visible-message", 101L, "n1", "bot"),
                    ),
                ),
                streamingText = "",
                onSend = { _, _ -> },
                onPickImage = {},
                onCancel = {},
                pendingAttachments = listOf(
                    PendingAttachment(
                        mimeType = "application/pdf",
                        displayName = "layout-proof.pdf",
                        bytes = ByteArray(1),
                        kind = Kind.FILE,
                        sizeBytes = 1L,
                    ),
                ),
            )
        }
        composeRule.waitForIdle()

        val lastMessage = composeRule.onNodeWithText("bottom-visible-message").fetchSemanticsNode().boundsInRoot
        val inputTop = composeRule.onNodeWithText("layout-proof.pdf").fetchSemanticsNode().boundsInRoot.top

        check(lastMessage.bottom <= inputTop)
    }

    @Test
    fun chatScreen_shows_typing_fallback_when_streaming_without_text() {
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    serverId = "s1",
                    nodeId = "n1",
                    nodeName = "bot",
                    isStreaming = true,
                ),
                streamingText = "",
                onSend = { _, _ -> },
                onPickImage = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Assistant is typing...").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun chatScreen_openDmAction_invokesCallback() {
        var opened: Triple<String, String, String>? = null
        composeRule.setContent {
            ChatScreen(
                state = ChatUiState(
                    serverId = "s1",
                    nodeId = "parent-1",
                    nodeName = "main",
                    messages = listOf(
                        DmMessage(
                            id = "spawn-child-1",
                            role = DmRole.SYSTEM,
                            content = "已创建 worker",
                            timestamp = 100L,
                            nodeId = "parent-1",
                            nodeName = "main",
                            action = com.nerve.android.domain.dm.DmAction.OpenDm("s1", "child-1", "worker"),
                        ),
                    ),
                ),
                streamingText = "",
                onSend = { _, _ -> },
                onPickImage = {},
                onCancel = {},
                onOpenDm = { serverId, nodeId, nodeName ->
                    opened = Triple(serverId, nodeId, nodeName)
                },
            )
        }

        composeRule.onNodeWithText("已创建 worker").assertIsDisplayed()
        composeRule.onNodeWithText("进入私聊").performClick()
        check(opened == Triple("s1", "child-1", "worker"))
    }

    @Test
    fun messageList_retriggers_auto_scroll_when_streaming_text_grows() {
        var streamingText by mutableStateOf("a")
        var autoScrollCalls = 0

        composeRule.setContent {
            MessageList(
                messages = listOf(
                    DmMessage("m1", DmRole.USER, "hello", 100L, "n1", "bot"),
                ),
                isStreaming = true,
                streamingText = streamingText,
                onAutoScroll = { autoScrollCalls += 1 },
            )
        }
        composeRule.waitForIdle()

        streamingText = "ab"
        composeRule.waitForIdle()

        check(autoScrollCalls == 2)
    }

    @Test
    fun assistantMarkdown_usesBubbleBackgroundWithoutExtraFontPadding() {
        composeRule.setContent {
            MessageBubble(
                message = DmMessage(
                    id = "markdown",
                    role = DmRole.ASSISTANT,
                    content = """
                        companion-agent just now

                        说明 `inline code` 和中文 English mixed content should wrap naturally.

                        ```kotlin
                        fun main() {
                            println("black code block")
                        }
                        ```
                    """.trimIndent(),
                    timestamp = 100L,
                    nodeId = "n1",
                    nodeName = "companion-agent",
                ),
                testTag = "assistant-markdown-bubble",
            )
        }
        composeRule.onNodeWithTag("assistant-markdown-bubble").assertIsDisplayed()
        composeRule.waitForIdle()

        val markdownTextView = composeRule.runOnUiThread {
            composeRule.activity.window.decorView
                .findTextViews()
                .single { it.text.contains("black code block") }
        }

        check(!markdownTextView.includeFontPadding)
        val background = markdownTextView.background as? ColorDrawable
        check(background?.color == WhiteSurface.toArgb())
    }

    @Test
    fun adjacentAssistantMarkdownBubbles_keepReadableVerticalSeparation() {
        var expectedGapPx = 0f
        composeRule.setContent {
            val density = LocalDensity.current
            expectedGapPx = with(density) { 8.dp.toPx() }
            Column {
                MessageBubble(
                    message = DmMessage(
                        id = "markdown-1",
                        role = DmRole.ASSISTANT,
                        content = """
                            ```kotlin
                            println("first")
                            ```
                        """.trimIndent(),
                        timestamp = 100L,
                        nodeId = "n1",
                        nodeName = "bot",
                    ),
                    testTag = "assistant-markdown-first",
                )
                MessageBubble(
                    message = DmMessage(
                        id = "markdown-2",
                        role = DmRole.ASSISTANT,
                        content = "说明 `inline code` should not press into the previous block.",
                        timestamp = 101L,
                        nodeId = "n1",
                        nodeName = "bot",
                    ),
                    testTag = "assistant-markdown-second",
                )
            }
        }
        composeRule.waitForIdle()

        val first = composeRule.onNodeWithTag("assistant-markdown-first").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("assistant-markdown-second").fetchSemanticsNode().boundsInRoot

        check(second.top - first.bottom >= expectedGapPx)
    }

    @Test
    fun chatRoute_enters_leaves_sends_cancels_and_shows_streaming_messages() {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = RecordingServerRegistry()
        val client = RecordingNerveClient()
        registry.clients["s1"] = client
        val viewModel = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)
        var show by mutableStateOf(true)

        composeRule.setContent {
            if (show) {
                ChatRoute(
                    viewModel = viewModel,
                    serverRegistry = registry,
                    serverId = "s1",
                    nodeId = "n1",
                    nodeName = "bot",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Start the conversation").assertIsDisplayed()
        check(client.subscribeCalls == listOf("n1"))

        sessionManager.onEvent(DmMappedEvent.AgentMessageStart(nodeId = "n1", nodeName = "bot", timestamp = 99L, messageId = "m1"))
        sessionManager.onEvent(DmMappedEvent.AgentMessageChunk(nodeId = "n1", text = "from-store", timestamp = 100L))
        sessionManager.onEvent(DmMappedEvent.AgentMessageEnd(nodeId = "n1", nodeName = "bot", timestamp = 101L, fallbackText = null))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("from-store").assertIsDisplayed()

        composeRule.onNodeWithText("Message").performTextInput("hello route")
        composeRule.onNodeWithText("Message").performImeAction()
        composeRule.waitForIdle()
        check(client.promptCalls == listOf("n1" to "hello route"))

        check(
            registry.eventsFlow.tryEmit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject { put("sessionUpdate", "agent_message_start") })
                    },
                ),
            ),
        ),
        )
        check(
            registry.eventsFlow.tryEmit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_message_chunk")
                            put("content", buildJsonObject { put("text", "stream body") })
                        })
                    },
                ),
            ),
        ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("stream body").assertIsDisplayed()
        composeRule.onNodeWithTag("streaming-bubble-assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        check(client.stopCalls == listOf("n1"))

        check(
            registry.eventsFlow.tryEmit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot",
                    detail = buildJsonObject {
                        put("update", buildJsonObject { put("sessionUpdate", "agent_message_end") })
                    },
                ),
            ),
        ),
        )
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("stream body").assertCountEquals(1) // finalized in messages

        show = false
        composeRule.waitForIdle()
        check(client.unsubscribeCalls == listOf("n1"))
    }

    @Test
    fun chatRoute_clears_streaming_text_on_idle_and_session_switch() {
        val sessionManager = DmSessionManager()
        val mapper = DmEventMapper()
        val registry = RecordingServerRegistry()
        registry.clients["s1"] = RecordingNerveClient()
        val viewModel = ChatViewModel(sessionManager, mapper, registry, Dispatchers.Unconfined)
        var nodeId by mutableStateOf("n1")
        var nodeName by mutableStateOf("bot-1")

        composeRule.setContent {
            ChatRoute(
                viewModel = viewModel,
                serverRegistry = registry,
                serverId = "s1",
                nodeId = nodeId,
                nodeName = nodeName,
            )
        }
        composeRule.waitForIdle()

        check(
            registry.eventsFlow.tryEmit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot-1",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_message_chunk")
                            put("content", buildJsonObject { put("text", "partial") })
                        })
                    },
                ),
            ),
        ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("partial").assertIsDisplayed()

        check(
            registry.eventsFlow.tryEmit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeStatusChanged("n1", "idle", buildJsonObject {}),
            ),
        ),
        )
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("partial").assertCountEquals(1) // flushed to finalized messages

        check(
            registry.eventsFlow.tryEmit(
            ServerScopedEvent(
                "s1",
                NerveEvent.NodeUpdate(
                    nodeId = "n1",
                    name = "bot-1",
                    detail = buildJsonObject {
                        put("update", buildJsonObject {
                            put("sessionUpdate", "agent_message_chunk")
                            put("content", buildJsonObject { put("text", "partial-2") })
                        })
                    },
                ),
            ),
        ),
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("partial-2").assertIsDisplayed()

        nodeId = "n2"
        nodeName = "bot-2"
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("partial-2").assertCountEquals(0)
    }
}

private fun View.findTextViews(): List<TextView> {
    val self = if (this is TextView) listOf(this) else emptyList()
    if (this !is ViewGroup) return self
    return self + (0 until childCount).flatMap { getChildAt(it).findTextViews() }
}

@RunWith(AndroidJUnit4::class)
class MainActivityChatSmokeTest {
    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_smoke_uses_nerve_app_assembly_and_starts_once() {
        check(activityRule.activity.application is NerveApp)
        check(debugInt("debugStartCalls") == 1)
        check(debugList("debugEnteredDmKeys") == listOf("s1:n1"))
        activityRule.onAllNodesWithText("Nerve App").assertCountEquals(0)
        activityRule.onNodeWithText("Message").assertIsDisplayed()
        activityRule.onNodeWithText("Send").assertIsDisplayed()
        activityRule.onNodeWithText("Cancel").assertIsDisplayed()
        activityRule.onNodeWithText("Start the conversation").assertIsDisplayed()
        activityRule.onNodeWithText("Assistant is typing...").assertIsDisplayed()
    }

    @Test
    fun mainActivity_smoke_waits_for_start_before_chatRoute_and_keeps_subscribe() {
        val trace = debugList("debugAssemblyTrace")
        val startEnd = trace.indexOf("start:end")
        val enterDm = trace.indexOf("chatRoute:enterDm:s1:n1")
        val subscribe = trace.indexOf("client:subscribe:s1:n1")

        check(startEnd >= 0)
        check(enterDm > startEnd)
        check(subscribe > enterDm)
        check(debugList("debugSubscribedDmKeys") == listOf("s1:n1"))
    }

    @Test
    fun mainActivity_smoke_uses_production_clientFactory_for_server() {
        check(debugList("debugClientFactoryTrace") == listOf("real:s1"))
    }

    @Test
    fun mainActivity_smoke_uses_resolved_entry_or_empty_state_not_hardcoded_s1_n1() {
        val resolvedEntry = debugString("debugResolvedEntryKey")
        val availableEntries = debugList("debugAvailableEntryKeys")

        if (resolvedEntry == null) {
            check(availableEntries.isEmpty())
            check(debugList("debugEnteredDmKeys").isEmpty())
            activityRule.onNodeWithText("Send").assertIsNotEnabled()
        } else {
            check(availableEntries.isNotEmpty())
            check(resolvedEntry == availableEntries.first())
            check(debugList("debugEnteredDmKeys") == listOf(resolvedEntry))
            check(resolvedEntry != "s1:n1" || availableEntries == listOf("s1:n1"))
        }
    }

    private fun debugInt(methodName: String): Int {
        val method = activityRule.activity.application.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(activityRule.activity.application) as Int
    }

    private fun debugList(methodName: String): List<String> {
        val method = activityRule.activity.application.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(activityRule.activity.application) as List<String>
    }

    private fun debugString(methodName: String): String? {
        val method = activityRule.activity.application.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(activityRule.activity.application) as String?
    }
}

private class RecordingServerRegistry : ServerRegistry {
    override val servers: StateFlow<List<ServerConfig>> = MutableStateFlow(emptyList())
    override val connections: StateFlow<List<ServerConnection>> = MutableStateFlow(emptyList())
    override val nodes: StateFlow<List<ServerNode>> = MutableStateFlow(emptyList())
    override val channels: StateFlow<List<ServerChannel>> = MutableStateFlow(emptyList())
    val eventsFlow = MutableSharedFlow<ServerScopedEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<ServerScopedEvent> = eventsFlow

    val clients = linkedMapOf<String, RecordingNerveClient>()

    override suspend fun start(registration: ClientRegistration) = Unit
    override suspend fun refresh(serverId: String?) = Unit
    override suspend fun addServer(config: ServerConfig) = Unit
    override suspend fun removeServer(serverId: String) = Unit
    override suspend fun reorderServers(orderedIds: List<String>) = Unit
    override suspend fun client(serverId: String): NerveClient? = clients[serverId]
    override fun triggerReconnectAll() = Unit
}

private class RecordingNerveClient : NerveClient {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.CONNECTED)
    override val events: SharedFlow<NerveEvent> = MutableSharedFlow(extraBufferCapacity = 32)

    val subscribeCalls = mutableListOf<String>()
    val unsubscribeCalls = mutableListOf<String>()
    val promptCalls = mutableListOf<Pair<String, String>>()
    val stopCalls = mutableListOf<String>()

    override suspend fun connect(server: ServerConfig, registration: ClientRegistration) = Unit
    override suspend fun disconnect() = Unit
    override suspend fun call(method: String, params: JsonObject): JsonElement = buildJsonObject {}
    override suspend fun listNodes(): List<NodeInfo> = emptyList()
    override suspend fun listChannels(): List<ChannelInfo> = emptyList()

    override suspend fun subscribe(nodeId: String) {
        subscribeCalls += nodeId
    }

    override suspend fun unsubscribe(nodeId: String) {
        unsubscribeCalls += nodeId
    }

    override suspend fun prompt(
        nodeId: String,
        content: String,
        attachments: List<com.nerve.android.transport.PromptAttachment>,
    ): PromptResult {
        promptCalls += nodeId to content
        return PromptResult(stopReason = "stop")
    }

    override suspend fun spawnNode(adapter: String, name: String?, cwd: String?): SpawnResult =
        SpawnResult(nodeId = "n1")

    override suspend fun cancelNode(nodeId: String) {
        stopCalls += "cancel:$nodeId"
    }

    override suspend fun stopNode(nodeId: String) {
        stopCalls += nodeId
    }
}
