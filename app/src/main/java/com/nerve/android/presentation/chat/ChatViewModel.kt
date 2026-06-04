package com.nerve.android.presentation.chat

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.dm.ContentBlock
import com.nerve.android.domain.dm.DmAction
import com.nerve.android.domain.dm.DmEventMapper
import com.nerve.android.domain.dm.DmMappedEvent
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.domain.dm.DmSessionManager
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.PromptAttachment
import com.nerve.android.transport.SnapshotMessage
import com.nerve.android.upload.FileUploadClient
import com.nerve.android.upload.FileUploader
import com.nerve.android.upload.PendingFileUpload
import com.nerve.android.upload.UploadedFile
import com.nerve.android.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

class ChatViewModel(
    private val sessionManager: DmSessionManager,
    private val mapper: DmEventMapper,
    private val serverRegistry: ServerRegistry,
    dispatcher: CoroutineDispatcher,
    private val onSubscribed: ((String, String) -> Unit)? = null,
    private val fileUploader: FileUploadClient = FileUploader(OkHttpClient()),
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState = _uiState.asStateFlow()

    private var messageJob: Job? = null
    private var attachJob: Job? = null
    private var streamingJob: Job? = null
    private var currentServerId: String? = null
    private var currentNodeId: String? = null

    fun enterDm(serverId: String, nodeId: String, nodeName: String) {
        val previousServerId = currentServerId
        val previousNodeId = currentNodeId
        if (previousServerId != null && previousNodeId != null) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                runCatching {
                    serverRegistry.client(previousServerId)?.unsubscribe(previousNodeId)
                }.onFailure {
                    Logger.warn(
                        "ChatViewModel",
                        "dm_unsubscribe_fail",
                        mapOf("nodeId" to previousNodeId, "reason" to it.message),
                    )
                }
            }
            sessionManager.reset()
        }
        messageJob?.cancel()
        attachJob?.cancel()
        streamingJob?.cancel()
        currentServerId = serverId
        currentNodeId = nodeId
        Logger.debug("ChatViewModel", "dm_enter", mapOf("serverId" to serverId, "nodeId" to nodeId))
        _uiState.value = _uiState.value.copy(
            serverId = serverId,
            nodeId = nodeId,
            nodeName = nodeName,
            streamingMessage = null,
            isStreaming = false,
            errorMessage = null,
        )
        messageJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionManager.messages.collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
        streamingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessionManager.streamingMessage.collect { streaming ->
                _uiState.value = _uiState.value.copy(
                    streamingMessage = streaming,
                    isStreaming = streaming != null,
                )
            }
        }
        attachJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.events
                .filter { it.serverId == serverId }
                .collect { scopedEvent ->
                    val event = scopedEvent.event
                    val eventNodeId = when (event) {
                        is NerveEvent.NodeUpdate -> event.nodeId
                        is NerveEvent.NodeStatusChanged -> event.nodeId
                        is NerveEvent.MessageSnapshot -> event.nodeId
                        is NerveEvent.NodeSpawned -> event.spawnedByNodeId
                        else -> null
                    }
                    if (eventNodeId != nodeId) return@collect
                    when (event) {
                        is NerveEvent.MessageSnapshot -> {
                            Logger.debug(
                                "ChatViewModel",
                                "dm_snapshot_received",
                                mapOf("nodeId" to nodeId, "count" to event.messages.size),
                            )
                            sessionManager.replaceHistory(
                                event.messages.map { it.toDmMessage(serverId, event.name) },
                            )
                        }
                        is NerveEvent.NodeSpawned -> {
                            Logger.debug(
                                "ChatViewModel",
                                "dm_node_spawned",
                                mapOf("parentNodeId" to nodeId, "nodeId" to event.nodeId, "nodeName" to event.name),
                            )
                            sessionManager.addSystemMessage(
                                DmMessage(
                                    id = "spawn-${event.nodeId}-${System.currentTimeMillis()}",
                                    role = DmRole.SYSTEM,
                                    content = "已创建 ${event.name}",
                                    timestamp = System.currentTimeMillis(),
                                    nodeId = nodeId,
                                    nodeName = nodeName,
                                    blocks = listOf(ContentBlock.Text("已创建 ${event.name}")),
                                    action = DmAction.OpenDm(
                                        serverId = serverId,
                                        nodeId = event.nodeId,
                                        nodeName = event.name,
                                    ),
                                ),
                            )
                        }
                        else -> {
                            val mapped = mapper.map(event)
                            Logger.debug(
                                "ChatViewModel",
                                "dm_event_mapped",
                                mapOf(
                                    "mappedType" to mapped::class.simpleName,
                                    "eventType" to event::class.simpleName,
                                ),
                            )
                            if (mapped != DmMappedEvent.Ignore) {
                                sessionManager.onEvent(mapped)
                                if (mapped is DmMappedEvent.AgentMessageEnd || mapped is DmMappedEvent.NodeIdle) {
                                    _uiState.value = _uiState.value.copy(isSending = false)
                                }
                            }
                        }
                    }
                }
        }
        // Subscribe after collect is listening. Server sends message_snapshot
        // immediately on subscribe (including reconnect resubscribe), which the
        // collector above will apply via replaceHistory.
        scope.launch {
            runCatching {
                serverRegistry.client(serverId)?.subscribe(nodeId)
            }.onSuccess {
                onSubscribed?.invoke(serverId, nodeId)
            }.onFailure {
                Logger.warn("ChatViewModel", "dm_subscribe_fail", mapOf("nodeId" to nodeId, "reason" to it.message))
                _uiState.value = _uiState.value.copy(errorMessage = it.message)
            }
        }
    }

    private fun SnapshotMessage.toDmMessage(serverId: String, nodeName: String): DmMessage {
        val dmRole = when (role) {
            "user" -> DmRole.USER
            "agent" -> DmRole.ASSISTANT
            else -> DmRole.SYSTEM
        }
        val dmAction = action?.let {
            if (it.type == "open_dm" && it.nodeId != null && it.nodeName != null) {
                DmAction.OpenDm(serverId = serverId, nodeId = it.nodeId, nodeName = it.nodeName)
            } else {
                null
            }
        }
        return DmMessage(
            id = id,
            role = dmRole,
            content = text,
            timestamp = ts.toLong(),
            nodeId = nodeId,
            nodeName = nodeName,
            blocks = listOf(ContentBlock.Text(text)),
            action = dmAction,
        )
    }

    fun leaveDm() {
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        Logger.debug("ChatViewModel", "dm_leave", mapOf("serverId" to serverId, "nodeId" to nodeId))
        messageJob?.cancel()
        attachJob?.cancel()
        streamingJob?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                serverRegistry.client(serverId)?.unsubscribe(nodeId)
            }.onFailure {
                Logger.warn("ChatViewModel", "dm_unsubscribe_fail", mapOf("nodeId" to nodeId, "reason" to it.message))
            }
        }
        currentServerId = null
        currentNodeId = null
        sessionManager.reset()
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            streamingMessage = null,
            isStreaming = false,
            serverId = null,
            nodeId = null,
            nodeName = null,
        )
    }

    suspend fun sendMessage(text: String) {
        if (text.isBlank()) return
        if (_uiState.value.isStreaming || _uiState.value.isSending) return
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        val nodeName = _uiState.value.nodeName ?: nodeId
        Logger.debug(
            "ChatViewModel",
            "dm_send_begin",
            mapOf("serverId" to serverId, "nodeId" to nodeId, "len" to text.length),
        )
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        val messageId = "local-${System.currentTimeMillis()}"
        val userEvent = DmMappedEvent.UserMessage(
            nodeId = nodeId,
            nodeName = nodeName,
            content = text,
            timestamp = System.currentTimeMillis(),
            messageId = messageId,
        )
        sessionManager.onEvent(userEvent)
        val result = runCatching {
            serverRegistry.client(serverId)?.prompt(nodeId, text)
        }
        if (result.isFailure) {
            val error = result.exceptionOrNull()
            Logger.warn(
                "ChatViewModel",
                "dm_send_fail",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to error?.message),
            )
            _uiState.value = _uiState.value.copy(
                errorMessage = error?.message,
                failedMessages = _uiState.value.failedMessages + (messageId to text),
            )
        }
        _uiState.value = _uiState.value.copy(isSending = false)
    }

    suspend fun retryMessage(messageId: String) {
        val text = _uiState.value.failedMessages[messageId] ?: run {
            Logger.warn(
                "ChatViewModel",
                "dm_retry_unknown_id",
                mapOf("messageId" to messageId),
            )
            return
        }
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        Logger.debug(
            "ChatViewModel",
            "dm_retry_begin",
            mapOf("serverId" to serverId, "nodeId" to nodeId, "messageId" to messageId),
        )
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        val result = runCatching {
            serverRegistry.client(serverId)?.prompt(nodeId, text)
        }
        if (result.isSuccess) {
            _uiState.value = _uiState.value.copy(
                failedMessages = _uiState.value.failedMessages - messageId,
            )
        } else {
            val error = result.exceptionOrNull()
            Logger.warn(
                "ChatViewModel",
                "dm_retry_fail",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to error?.message),
            )
            _uiState.value = _uiState.value.copy(errorMessage = error?.message)
        }
        _uiState.value = _uiState.value.copy(isSending = false)
    }

    suspend fun sendImages(caption: String, attachments: List<PromptAttachment.Image>) {
        if (attachments.isEmpty()) return
        if (_uiState.value.isStreaming || _uiState.value.isSending) return
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        val nodeName = _uiState.value.nodeName ?: nodeId
        val text = caption.ifBlank { "Attached image(s)" }
        val localText = buildString {
            append(text)
            attachments.forEach { append("\n[image:${it.mimeType}]") }
        }
        Logger.debug(
            "ChatViewModel",
            "dm_image_send_begin",
            mapOf("serverId" to serverId, "nodeId" to nodeId, "imageCount" to attachments.size),
        )
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        sessionManager.onEvent(
            DmMappedEvent.UserMessage(
                nodeId = nodeId,
                nodeName = nodeName,
                content = localText,
                timestamp = System.currentTimeMillis(),
                messageId = "local-image-${System.currentTimeMillis()}",
            ),
        )
        runCatching {
            serverRegistry.client(serverId)?.prompt(
                nodeId,
                text,
                attachments,
            )
        }.onFailure {
            Logger.warn(
                "ChatViewModel",
                "dm_send_fail",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to it.message),
            )
            _uiState.value = _uiState.value.copy(errorMessage = it.message)
        }
        _uiState.value = _uiState.value.copy(isSending = false)
    }

    suspend fun sendFiles(caption: String, files: List<PendingFileUpload>) {
        if (files.isEmpty()) return
        if (_uiState.value.isStreaming || _uiState.value.isSending) return
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        val nodeName = _uiState.value.nodeName ?: nodeId
        val server = serverRegistry.servers.value.firstOrNull { it.id == serverId } ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "server config not found")
            return
        }
        Logger.debug(
            "ChatViewModel",
            "dm_file_send_begin",
            mapOf("serverId" to serverId, "nodeId" to nodeId, "fileCount" to files.size),
        )
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        val uploadResult = runCatching {
            withContext(Dispatchers.IO) {
                files.map { fileUploader.upload(server.httpUrl(), it) }
            }
        }
        if (uploadResult.isFailure) {
            val error = uploadResult.exceptionOrNull()
            Logger.warn(
                "ChatViewModel",
                "dm_file_upload_fail",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to error?.message),
            )
            val failedId = "local-file-failed-${System.currentTimeMillis()}"
            _uiState.value = _uiState.value.copy(
                isSending = false,
                errorMessage = error?.message,
                failedMessages = _uiState.value.failedMessages + (failedId to caption),
            )
            return
        }

        val uploaded = uploadResult.getOrThrow()
        val prompt = buildFilePrompt(caption, uploaded)
        val messageId = "local-file-${System.currentTimeMillis()}"
        sessionManager.onEvent(
            DmMappedEvent.UserMessage(
                nodeId = nodeId,
                nodeName = nodeName,
                content = prompt,
                timestamp = System.currentTimeMillis(),
                messageId = messageId,
            ),
        )
        runCatching {
            serverRegistry.client(serverId)?.prompt(nodeId, prompt)
        }.onFailure {
            Logger.warn(
                "ChatViewModel",
                "dm_send_fail",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to it.message),
            )
            _uiState.value = _uiState.value.copy(
                errorMessage = it.message,
                failedMessages = _uiState.value.failedMessages + (messageId to prompt),
            )
        }
        _uiState.value = _uiState.value.copy(isSending = false)
    }

    private suspend fun loadSessionHistory(serverId: String, nodeId: String, nodeName: String) {
        val client = serverRegistry.client(serverId) ?: return
        runCatching {
            val listResult = client.call(
                "session.list",
                buildJsonObject { put("nodeName", nodeName) },
            )
            val sessions = listResult.jsonObject["sessions"] as? JsonArray ?: return
            if (sessions.isEmpty()) return
            val latestSessionId = sessions.last()
                .jsonObject["sessionId"]?.jsonPrimitive?.content ?: return
            Logger.debug(
                "ChatViewModel",
                "session_load_begin",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "sessionId" to latestSessionId),
            )
            client.call(
                "session.load",
                buildJsonObject {
                    put("nodeName", nodeName)
                    put("sessionId", latestSessionId)
                },
            )
        }.onFailure {
            Logger.warn(
                "ChatViewModel",
                "session_load_fail",
                mapOf("serverId" to serverId, "nodeId" to nodeId, "reason" to it.message),
            )
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

private fun buildFilePrompt(caption: String, files: List<UploadedFile>): String = buildString {
    append("我上传了文件，请读取并参考：")
    files.forEach { file ->
        append("\n- name: ${file.name}")
        append("\n  path: ${file.path}")
        append("\n  mime: ${file.mimeType}")
        append("\n  size: ${file.sizeBytes} bytes")
        append("\n  sha256: ${file.sha256}")
    }
    val trimmed = caption.trim()
    if (trimmed.isNotEmpty()) {
        append("\n\n")
        append(trimmed)
    }
}
