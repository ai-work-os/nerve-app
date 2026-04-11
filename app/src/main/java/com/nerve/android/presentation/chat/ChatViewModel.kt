package com.nerve.android.presentation.chat

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.dm.DmEventProcessor
import com.nerve.android.domain.dm.DmKey
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.domain.dm.DmStore
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ChatViewModel(
    private val dmStore: DmStore,
    private val dmEventProcessor: DmEventProcessor,
    private val serverRegistry: ServerRegistry,
    dispatcher: CoroutineDispatcher,
    private val onSubscribed: ((String, String) -> Unit)? = null,
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
                serverRegistry.client(previousServerId)?.unsubscribe(previousNodeId)
            }
        }
        messageJob?.cancel()
        attachJob?.cancel()
        streamingJob?.cancel()
        currentServerId = serverId
        currentNodeId = nodeId
        val key = DmKey("$serverId:$nodeId")
        Logger.d("ChatViewModel", "chat enter key=${key.value}")
        _uiState.value = _uiState.value.copy(
            serverId = serverId,
            nodeId = nodeId,
            nodeName = nodeName,
            isStreaming = false,
            errorMessage = null,
        )
        messageJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dmStore.messages(key).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.client(serverId)?.subscribe(nodeId)
            onSubscribed?.invoke(serverId, nodeId)
        }
        attachJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            dmEventProcessor.attach(
                serverId,
                nodeId,
                serverRegistry.events
                    .filter { it.serverId == serverId }
                    .map { it.event },
            )
        }
        scope.launch {
            loadSessionHistory(serverId, nodeId, nodeName)
        }
        streamingJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.events
                .filter { it.serverId == serverId }
                .onEach { event ->
                    when (val inner = event.event) {
                        is NerveEvent.NodeUpdate -> {
                            if (inner.nodeId != nodeId) return@onEach
                            val updateKind = inner.detail["update"]
                                ?.jsonObject
                                ?.get("sessionUpdate")
                                ?.jsonPrimitive
                                ?.content
                            when (updateKind) {
                                "agent_message_start", "agent_message_chunk", "agent_thought_chunk" -> setStreaming(key.value, true)
                                "agent_message_end" -> setStreaming(key.value, false)
                            }
                        }
                        is NerveEvent.NodeStatusChanged -> {
                            if (inner.nodeId == nodeId && inner.status == "idle") {
                                setStreaming(key.value, false)
                            }
                        }
                        else -> Unit
                    }
                }
                .collect()
        }
    }

    fun leaveDm() {
        val serverId = currentServerId ?: return
        val nodeId = currentNodeId ?: return
        Logger.d("ChatViewModel", "chat leave key=$serverId:$nodeId")
        messageJob?.cancel()
        attachJob?.cancel()
        streamingJob?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.client(serverId)?.unsubscribe(nodeId)
        }
        currentServerId = null
        currentNodeId = null
        _uiState.value = _uiState.value.copy(
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
        val dmKey = DmKey("$serverId:$nodeId")
        val nodeName = _uiState.value.nodeName ?: nodeId
        Logger.d("ChatViewModel", "chat send key=${dmKey.value} len=${text.length}")
        _uiState.value = _uiState.value.copy(isSending = true, errorMessage = null)
        val userMessage = DmMessage(
            id = "local-${System.currentTimeMillis()}",
            role = DmRole.USER,
            content = text,
            timestamp = System.currentTimeMillis(),
            nodeId = nodeId,
            nodeName = nodeName,
        )
        dmStore.appendMessage(dmKey, userMessage)
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
            Logger.d("ChatViewModel", "chat session.load key=$serverId:$nodeId sessionId=$latestSessionId")
            client.call(
                "session.load",
                buildJsonObject {
                    put("nodeName", nodeName)
                    put("sessionId", latestSessionId)
                },
            )
        }.onFailure {
            Logger.w("ChatViewModel", "chat session load failed key=$serverId:$nodeId reason=${it.message}")
        }
    }

    private fun setStreaming(key: String, enabled: Boolean) {
        Logger.d("ChatViewModel", "chat stream key=$key state=${if (enabled) "on" else "off"}")
        _uiState.value = _uiState.value.copy(isStreaming = enabled)
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
