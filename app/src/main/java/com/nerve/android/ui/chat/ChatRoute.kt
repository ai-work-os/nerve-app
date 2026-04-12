package com.nerve.android.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.presentation.chat.ChatViewModel
import com.nerve.android.util.Logger
import kotlinx.coroutines.launch

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

    LaunchedEffect(serverId, nodeId, nodeName) {
        Logger.d("ChatRoute", "chat route enter key=$serverId:$nodeId")
        onEnter?.invoke(serverId, nodeId)
        viewModel.enterDm(serverId, nodeId, nodeName)
    }

    DisposableEffect(serverId, nodeId) {
        onDispose {
            Logger.d("ChatRoute", "chat route leave key=$serverId:$nodeId")
            viewModel.leaveDm()
        }
    }

    ChatScreen(
        state = state,
        streamingText = state.streamingMessage?.content ?: "",
        streamingBlocks = state.streamingMessage?.blocks ?: emptyList(),
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
