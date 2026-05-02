package com.nerve.android.presentation.nodes

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveEvent
import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NodesViewModel(
    private val serverRegistry: ServerRegistry,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _uiState = MutableStateFlow(NodesUiState())
    val uiState = _uiState.asStateFlow()
    private val statuses = ConcurrentHashMap<String, String>()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.connections.collect { connections ->
                _uiState.value = _uiState.value.copy(
                    connectedCount = connections.count { it.state == ConnectionState.CONNECTED },
                    totalCount = connections.size,
                )
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.nodes.collect { publishItems() }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.events.collect { scoped ->
                when (val event = scoped.event) {
                    is NerveEvent.NodeStatusChanged -> statuses["${scoped.serverId}:${event.nodeId}"] = event.status
                    is NerveEvent.NodeRegistered -> statuses["${scoped.serverId}:${event.nodeId}"] = "idle"
                    is NerveEvent.NodeStopped -> statuses["${scoped.serverId}:${event.nodeId}"] = "stopped"
                    else -> Unit
                }
                publishItems()
            }
        }
    }

    suspend fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
        runCatching { serverRegistry.refresh() }
            .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        _uiState.value = _uiState.value.copy(isRefreshing = false)
    }

    suspend fun spawnNode(serverId: String, adapter: String, name: String?, cwd: String?) {
        Logger.debug("NodesViewModel", "node_spawn_begin", mapOf("serverId" to serverId, "adapter" to adapter))
        runCatching {
            serverRegistry.client(serverId)?.spawnNode(adapter, name, cwd)
            serverRegistry.refresh(serverId)
        }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = it.message)
        }
    }

    suspend fun stopNode(serverId: String, nodeId: String) {
        Logger.debug("NodesViewModel", "node_stop_begin", mapOf("serverId" to serverId, "nodeId" to nodeId))
        runCatching {
            serverRegistry.client(serverId)?.stopNode(nodeId)
            serverRegistry.refresh(serverId)
        }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = it.message)
        }
    }

    private fun publishItems() {
        _uiState.value = _uiState.value.copy(
            items = serverRegistry.nodes.value.map { node ->
                NodeItemUi(
                    serverId = node.serverId,
                    serverName = node.serverName,
                    nodeId = node.node.id,
                    nodeName = node.node.name,
                    status = statuses["${node.serverId}:${node.node.id}"] ?: node.node.status,
                    adapter = node.node.adapter,
                    cwd = node.node.cwd,
                )
            },
        )
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
