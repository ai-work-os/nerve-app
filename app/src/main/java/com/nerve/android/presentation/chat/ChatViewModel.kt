package com.nerve.android.presentation.chat

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.dm.ContentBlock
import com.nerve.android.domain.dm.DmEventMapper
import com.nerve.android.domain.dm.DmMappedEvent
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.domain.dm.DmSessionManager
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.SnapshotMessage
import com.nerve.android.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ChatViewModel(
    private val sessionManager: DmSessionManager,
    private val mapper: DmEventMapper,
    private val serverRegistry: ServerRegistry,
    dispatcher: CoroutineDispatcher,
    private val onSubscribed: ((String, String) -> Unit)? = null,
) : ViewModel() {
    companion object {
        private const val DEFAULT_CLIENT_NAME = "android-ui"
    }

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
                serverRegistry.client(previousServerId)?.unsubscribe(previousNodeId)
            }
            sessionManager.reset()
        }
        messageJob?.cancel()
        attachJob?.cancel()
        streamingJob?.cancel()
        currentServerId = serverId
        currentNodeId = nodeId
        Logger.d("ChatViewModel", "chat enter serverId=$serverId nodeId=$nodeId")
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
                        else -> null
                    }
                    if (eventNodeId != nodeId) return@collect
                    when (event) {
                        is NerveEvent.MessageSnapshot -> {
                            Logger.d(
                                "ChatViewModel",
                                "snapshot received nodeId=$nodeId count=${event.messages.size}",
                            )
                            sessionManager.replaceHistory(
                                event.messages.map { it.toDmMessage(event.name) },
                            )
                        }
                        else -> {
                            val mapped = mapper.map(event)
                            Logger.d(
                                "ChatViewModel",
                                "event mapped: ${mapped::class.simpleName} from ${event::class.simpleName}",
                            )
                            if (mapped != DmMappedEvent.Ignore) {
                                sessionManager.onEvent(mapped)
                            }
                        }
                    }
                }
        }
        // Subscribe after collect is listening. Server sends message_snapshot
        // immediately on subscribe (including reconnect resubscribe), which the
        // collector above will apply via replaceHistory.
        scope.launch {
            serverRegistry.client(serverId)?.subscribe(nodeId)
            onSubscribed?.invoke(serverId, nodeId)
        }
    }

    private fun SnapshotMessage.toDmMessage(nodeName: String): DmMessage {
        val dmRole = when (role) {
            "user" -> DmRole.USER
            "agent" -> DmRole.ASSISTANT
            else -> DmRole.SYSTEM
        }
        return DmMessage(
            id = id,
            role = dmRole,
            content = text,
            timestamp = ts.toLong(),
            nodeId = nodeId,
            nodeName = nodeName,
            blocks = listOf(ContentBlock.Text(text)),
        )
    }

    fun leaveDm() {
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        Logger.d("ChatViewModel", "chat leave serverId=$serverId nodeId=$nodeId")
        messageJob?.cancel()
        attachJob?.cancel()
        streamingJob?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.client(serverId)?.unsubscribe(nodeId)
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
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        val nodeName = _uiState.value.nodeName ?: nodeId
        val tagged = "$DEFAULT_CLIENT_NAME: $text"
        Logger.d("ChatViewModel", "chat send nodeId=$nodeId len=${text.length}")
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        val userEvent = DmMappedEvent.UserMessage(
            nodeId = nodeId,
            nodeName = nodeName,
            content = tagged,
            timestamp = System.currentTimeMillis(),
            messageId = "local-${System.currentTimeMillis()}",
        )
        sessionManager.onEvent(userEvent)
        runCatching {
            serverRegistry.client(serverId)?.prompt(nodeId, text)
        }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = it.message)
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
            Logger.d("ChatViewModel", "chat session.load serverId=$serverId nodeId=$nodeId sessionId=$latestSessionId")
            client.call(
                "session.load",
                buildJsonObject {
                    put("nodeName", nodeName)
                    put("sessionId", latestSessionId)
                },
            )
        }.onFailure {
            Logger.w("ChatViewModel", "chat session load failed serverId=$serverId nodeId=$nodeId reason=${it.message}")
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
