package com.nerve.android.ui.chat

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
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
    onOpenDm: ((String, String, String) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: "image/*"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Logger.d("ChatRoute", "chat ui image selected key=$serverId:$nodeId mime=$mimeType bytes=${bytes.size}")
        scope.launch { viewModel.sendImage("", mimeType, base64) }
    }

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
        onPickImage = { imagePicker.launch("image/*") },
        onCancel = {
            Logger.d("ChatRoute", "chat ui cancel key=$serverId:$nodeId")
            scope.launch { serverRegistry.client(serverId)?.cancelNode(nodeId) }
        },
        onStop = {
            Logger.d("ChatRoute", "chat ui stop key=$serverId:$nodeId")
            scope.launch { serverRegistry.client(serverId)?.stopNode(nodeId) }
        },
        onBack = onBack,
        onOpenDm = onOpenDm,
    )
}
