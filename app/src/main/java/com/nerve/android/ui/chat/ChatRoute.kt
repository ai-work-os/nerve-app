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

    LaunchedEffect(serverId, nodeId, nodeName) {
        Logger.d("ChatRoute", "chat route enter key=$serverId:$nodeId")
        onEnter?.invoke(serverId, nodeId)
        viewModel.enterDm(serverId, nodeId, nodeName)
    }

    DisposableEffect(serverId, nodeId) {
        onDispose {
            streamingText = ""
            Logger.d("ChatRoute", "chat route leave key=$serverId:$nodeId")
            viewModel.leaveDm()
        }
    }

    LaunchedEffect(serverRegistry, serverId, nodeId) {
        streamingText = ""
        serverRegistry.events.collect { scopedEvent ->
            if (scopedEvent.serverId != serverId) return@collect
            when (val event = scopedEvent.event) {
                is NerveEvent.NodeUpdate -> {
                    if (event.nodeId != nodeId) return@collect
                    val update = event.detail["update"]?.jsonObject ?: return@collect
                    when (update["sessionUpdate"]?.jsonPrimitive?.content) {
                        "agent_message_start" -> streamingText = ""
                        "agent_message_chunk" -> {
                            val chunk = update["content"]
                                ?.jsonObject
                                ?.get("text")
                                ?.jsonPrimitive
                                ?.content
                                .orEmpty()
                            if (chunk.isNotEmpty()) {
                                streamingText += chunk
                            }
                        }
                        "agent_message_end" -> streamingText = ""
                    }
                }
                is NerveEvent.NodeStatusChanged -> {
                    if (event.nodeId == nodeId && event.status == "idle") {
                        streamingText = ""
                    }
                }
                else -> Unit
            }
        }
    }

    ChatScreen(
        state = state,
        streamingText = streamingText,
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
