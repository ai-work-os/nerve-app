package com.nerve.android.presentation.channels

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.channel.ChannelEventProcessor
import com.nerve.android.domain.channel.ChannelKey
import com.nerve.android.domain.channel.ChannelStore
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChannelsViewModel(
    private val channelStore: ChannelStore,
    private val channelEventProcessor: ChannelEventProcessor,
    private val serverRegistry: ServerRegistry,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState = _uiState.asStateFlow()
    private val attachedServers = ConcurrentHashMap.newKeySet<String>()
    private var messagesJob: Job? = null
    private var metaJob: Job? = null

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.channels.collect { channels ->
                _uiState.value = _uiState.value.copy(channels = channels)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.events.collect { scoped ->
                when (scoped.event) {
                    is NerveEvent.ChannelCreated, is NerveEvent.ChannelClosed -> serverRegistry.refresh(scoped.serverId)
                    else -> Unit
                }
            }
        }
    }

    fun enterChannel(serverId: String, channelId: String, channelName: String) {
        ensureAttached(serverId)
        messagesJob?.cancel()
        metaJob?.cancel()
        val key = ChannelKey("$serverId:$channelId")
        Logger.d("ChannelsViewModel", "channels enter key=${key.value}")
        _uiState.value = _uiState.value.copy(
            currentServerId = serverId,
            currentChannelId = channelId,
            currentChannelName = channelName,
            errorMessage = null,
        )
        messagesJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            channelStore.messages(key).collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
        metaJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            channelStore.meta(key).collect { meta ->
                _uiState.value = _uiState.value.copy(currentMeta = meta)
            }
        }
    }

    suspend fun joinChannel(serverId: String, channelId: String) {
        Logger.d("ChannelsViewModel", "channels join key=$serverId:$channelId")
        _uiState.value = _uiState.value.copy(isJoining = true, errorMessage = null)
        runCatching {
            serverRegistry.client(serverId)?.call(
                "channel.join",
                buildJsonObject { put("channelId", channelId) },
            )
            serverRegistry.refresh(serverId)
        }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = it.message)
        }
        _uiState.value = _uiState.value.copy(isJoining = false)
    }

    suspend fun sendMessage(text: String) {
        if (text.isBlank()) return
        val serverId = _uiState.value.currentServerId ?: return
        val channelId = _uiState.value.currentChannelId ?: return
        Logger.d("ChannelsViewModel", "channels post key=$serverId:$channelId len=${text.length}")
        _uiState.value = _uiState.value.copy(isPosting = true, errorMessage = null)
        runCatching {
            serverRegistry.client(serverId)?.call(
                "channel.post",
                buildJsonObject {
                    put("channelId", channelId)
                    put("content", text)
                },
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = it.message)
        }
        _uiState.value = _uiState.value.copy(isPosting = false)
    }

    private fun ensureAttached(serverId: String) {
        if (!attachedServers.add(serverId)) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            channelEventProcessor.attach(
                serverId,
                serverRegistry.events
                    .filter { it.serverId == serverId }
                    .map { it.event },
            )
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
