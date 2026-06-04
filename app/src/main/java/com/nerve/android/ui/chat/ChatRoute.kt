package com.nerve.android.ui.chat

import android.util.Base64
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.presentation.chat.ChatViewModel
import com.nerve.android.transport.PromptAttachment
import com.nerve.android.upload.PendingFileUpload
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
    var pendingAttachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val attachments = uris.mapNotNull { uri ->
            val mimeType = resolver.getType(uri) ?: "image/*"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Logger.debug(
                "ChatRoute",
                "dm_image_picked",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "mime" to mimeType, "bytes" to bytes.size),
            )
            PendingAttachment(
                mimeType = mimeType,
                base64Data = base64,
                displayName = context.displayName(uri),
                kind = Kind.IMAGE,
                sizeBytes = bytes.size.toLong(),
            )
        }
        pendingAttachments = pendingAttachments + attachments
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val attachments = uris.mapNotNull { uri ->
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            Logger.debug(
                "ChatRoute",
                "dm_file_picked",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "mime" to mimeType, "bytes" to bytes.size),
            )
            PendingAttachment(
                mimeType = mimeType,
                displayName = context.displayName(uri),
                bytes = bytes,
                kind = Kind.FILE,
                sizeBytes = bytes.size.toLong(),
            )
        }
        pendingAttachments = pendingAttachments + attachments
    }

    LaunchedEffect(serverId, nodeId, nodeName) {
        Logger.debug("ChatRoute", "route_enter", mapOf("serverId" to serverId, "nodeId" to nodeId, "route" to "dm"))
        onEnter?.invoke(serverId, nodeId)
        viewModel.enterDm(serverId, nodeId, nodeName)
    }

    DisposableEffect(serverId, nodeId) {
        onDispose {
            Logger.debug(
                "ChatRoute",
                "route_leave",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "route" to "dm"),
            )
            viewModel.leaveDm()
        }
    }

    ChatScreen(
        state = state,
        streamingText = state.streamingMessage?.content ?: "",
        streamingBlocks = state.streamingMessage?.blocks ?: emptyList(),
        canSend = !state.isStreaming,
        onSend = { text, attachments ->
            val fileAttachments = attachments.filter { it.kind == Kind.FILE }
            val imageAttachments = attachments.filter { it.kind == Kind.IMAGE }
            if (fileAttachments.isNotEmpty()) {
                Logger.debug(
                    "ChatRoute",
                    "dm_send_with_file_begin",
                    mapOf(
                        "serverId" to serverId,
                        "nodeId" to nodeId,
                        "len" to text.length,
                        "fileCount" to fileAttachments.size,
                    ),
                )
                pendingAttachments = emptyList()
                scope.launch {
                    viewModel.sendFiles(
                        text,
                        fileAttachments.map {
                            PendingFileUpload(
                                name = it.displayName ?: "upload.bin",
                                mimeType = it.mimeType,
                                bytes = it.bytes ?: ByteArray(0),
                            )
                        },
                    )
                }
            } else if (imageAttachments.isNotEmpty()) {
                Logger.debug(
                    "ChatRoute",
                    "dm_send_with_image_begin",
                    mapOf(
                        "serverId" to serverId,
                        "nodeId" to nodeId,
                        "len" to text.length,
                        "imageCount" to imageAttachments.size,
                    ),
                )
                pendingAttachments = emptyList()
                scope.launch {
                    viewModel.sendImages(
                        text,
                        imageAttachments.mapNotNull { attachment ->
                            attachment.base64Data?.let { data ->
                                PromptAttachment.Image(mimeType = attachment.mimeType, data = data)
                            }
                        },
                    )
                }
            } else {
                Logger.debug(
                    "ChatRoute",
                    "dm_send_begin",
                    mapOf("serverId" to serverId, "nodeId" to nodeId, "len" to text.length),
                )
                scope.launch { viewModel.sendMessage(text) }
            }
        },
        onPickImage = { imagePicker.launch("image/*") },
        onPickFile = { filePicker.launch("*/*") },
        onRetry = { messageId ->
            Logger.debug("ChatRoute", "dm_retry", mapOf("serverId" to serverId, "nodeId" to nodeId, "messageId" to messageId))
            scope.launch { viewModel.retryMessage(messageId) }
        },
        onCancel = {
            Logger.debug("ChatRoute", "dm_cancel_begin", mapOf("serverId" to serverId, "nodeId" to nodeId))
            scope.launch { serverRegistry.client(serverId)?.cancelNode(nodeId) }
        },
        onStop = {
            Logger.debug("ChatRoute", "dm_stop_begin", mapOf("serverId" to serverId, "nodeId" to nodeId))
            scope.launch { serverRegistry.client(serverId)?.stopNode(nodeId) }
        },
        onBack = onBack,
        onOpenDm = onOpenDm,
        pendingAttachments = pendingAttachments,
        onRemoveAttachment = { index ->
            Logger.debug("ChatRoute", "dm_attachment_cleared", mapOf("serverId" to serverId, "nodeId" to nodeId, "index" to index))
            pendingAttachments = pendingAttachments.filterIndexed { i, _ -> i != index }
        },
    )
}

private fun android.content.Context.displayName(uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment
}
