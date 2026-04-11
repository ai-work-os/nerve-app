package com.nerve.android.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nerve.android.domain.dm.ContentBlock
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.presentation.chat.ChatViewModel
import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ChatRoute(
    viewModel: ChatViewModel,
    serverRegistry: ServerRegistry,
    serverId: String,
    nodeId: String,
    nodeName: String,
    onBack: (() -> Unit)? = null,
    onEnter: ((String, String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var streamingText by remember(serverId, nodeId) { mutableStateOf("") }
    var streamingBlocks by remember(serverId, nodeId) { mutableStateOf<List<ContentBlock>>(emptyList()) }

    LaunchedEffect(serverId, nodeId, nodeName) {
        Logger.d("ChatRoute", "chat route enter key=$serverId:$nodeId")
        onEnter?.invoke(serverId, nodeId)
        viewModel.enterDm(serverId, nodeId, nodeName)
    }

    DisposableEffect(serverId, nodeId) {
        onDispose {
            streamingText = ""
            streamingBlocks = emptyList()
            Logger.d("ChatRoute", "chat route leave key=$serverId:$nodeId")
            viewModel.leaveDm()
        }
    }

    LaunchedEffect(serverRegistry, serverId, nodeId) {
        streamingText = ""
        streamingBlocks = emptyList()
        serverRegistry.events.collect { scopedEvent ->
            if (scopedEvent.serverId != serverId) return@collect
            try { when (val event = scopedEvent.event) {
                is NerveEvent.NodeUpdate -> {
                    if (event.nodeId != nodeId) return@collect
                    val update = event.detail["update"]?.jsonObject ?: return@collect
                    when (update["sessionUpdate"]?.jsonPrimitive?.content) {
                        "agent_message_start" -> {
                            streamingText = ""
                            streamingBlocks = emptyList()
                        }
                        "agent_thought_chunk" -> {
                            val text = (update["content"] as? kotlinx.serialization.json.JsonObject)
                                ?.get("text")
                                ?.jsonPrimitive
                                ?.content
                                .orEmpty()
                            if (text.isNotEmpty()) {
                                val blocks = streamingBlocks.toMutableList()
                                val last = blocks.lastOrNull()
                                if (last is ContentBlock.Thinking && !last.completed) {
                                    blocks[blocks.lastIndex] = ContentBlock.Thinking(last.text + text)
                                } else {
                                    blocks.add(ContentBlock.Thinking(text))
                                }
                                streamingBlocks = blocks
                            }
                        }
                        "agent_message_chunk" -> {
                            val chunk = (update["content"] as? kotlinx.serialization.json.JsonObject)
                                ?.get("text")
                                ?.jsonPrimitive
                                ?.content
                                .orEmpty()
                            if (chunk.isNotEmpty()) {
                                streamingText += chunk
                                val blocks = streamingBlocks.toMutableList()
                                val last = blocks.lastOrNull()
                                if (last is ContentBlock.Text) {
                                    blocks[blocks.lastIndex] = ContentBlock.Text(last.text + chunk)
                                } else {
                                    blocks.add(ContentBlock.Text(chunk))
                                }
                                streamingBlocks = blocks
                            }
                        }
                        "tool_call" -> {
                            val toolId: String
                            val toolName: String
                            val input: String
                            val acpId = update["toolCallId"]?.jsonPrimitive?.content
                            if (acpId != null) {
                                toolId = acpId
                                toolName = runCatching {
                                    update["_meta"]?.jsonObject
                                        ?.get("claudeCode")?.jsonObject
                                        ?.get("toolName")?.jsonPrimitive?.content
                                }.getOrNull()
                                    ?: update["title"]?.jsonPrimitive?.content
                                    ?: ""
                                input = update["rawInput"]?.jsonPrimitive?.content ?: ""
                            } else {
                                val legacy = update["toolCall"] as? kotlinx.serialization.json.JsonObject
                                val content = legacy ?: update["content"] as? kotlinx.serialization.json.JsonObject
                                toolId = content?.get("id")?.jsonPrimitive?.content ?: ""
                                toolName = content?.get("name")?.jsonPrimitive?.content ?: ""
                                input = content?.get("input")?.toString() ?: ""
                            }
                            val blocks = streamingBlocks.toMutableList()
                            val last = blocks.lastOrNull()
                            if (last is ContentBlock.Thinking && !last.completed) {
                                blocks[blocks.lastIndex] = ContentBlock.Thinking(last.text, completed = true)
                            }
                            blocks.add(ContentBlock.ToolCall(toolId = toolId, toolName = toolName, input = input))
                            streamingBlocks = blocks
                        }
                        "agent_message_end" -> {
                            streamingText = ""
                            streamingBlocks = emptyList()
                        }
                    }
                }
                is NerveEvent.NodeStatusChanged -> {
                    if (event.nodeId == nodeId && event.status == "idle") {
                        streamingText = ""
                        streamingBlocks = emptyList()
                    }
                }
                else -> Unit
            } } catch (e: Exception) {
                Logger.w("ChatRoute", "chat event error: ${e.message}")
            }
        }
    }

    ChatScreen(
        state = state,
        streamingText = streamingText,
        streamingBlocks = streamingBlocks,
        onSend = { text ->
            Logger.d("ChatRoute", "chat ui send key=$serverId:$nodeId len=${text.length}")
            scope.launch { viewModel.sendMessage(text) }
        },
        onCancel = {
            Logger.d("ChatRoute", "chat ui cancel key=$serverId:$nodeId")
            scope.launch { serverRegistry.client(serverId)?.cancelNode(nodeId) }
        },
        onStop = {
            Logger.d("ChatRoute", "chat ui stop key=$serverId:$nodeId")
            scope.launch { serverRegistry.client(serverId)?.stopNode(nodeId) }
        },
        onBack = onBack,
    )
}
