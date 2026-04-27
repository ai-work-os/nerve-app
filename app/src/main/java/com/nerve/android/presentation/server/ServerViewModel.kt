package com.nerve.android.presentation.server

import androidx.lifecycle.ViewModel
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.transport.ServerConfig
import com.nerve.android.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ServerViewModel(
    private val serverRegistry: ServerRegistry,
    dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.servers.collect { servers ->
                _uiState.value = _uiState.value.copy(servers = servers)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            serverRegistry.connections.collect { connections ->
                _uiState.value = _uiState.value.copy(connections = connections)
            }
        }
    }

    suspend fun addServer(id: String, name: String, address: String) {
        Logger.d("ServerViewModel", "server vm add id=$id address=$address")
        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        runCatching {
            serverRegistry.addServer(ServerConfig(id, name, address))
        }.onFailure {
            _uiState.value = _uiState.value.copy(errorMessage = it.message)
        }
        _uiState.value = _uiState.value.copy(isSubmitting = false)
    }

    suspend fun removeServer(serverId: String) {
        Logger.d("ServerViewModel", "server vm remove id=$serverId")
        runCatching { serverRegistry.removeServer(serverId) }
            .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
    }

    suspend fun refresh() {
        Logger.d("ServerViewModel", "server vm refresh")
        _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
        runCatching { serverRegistry.refresh() }
            .onFailure { _uiState.value = _uiState.value.copy(errorMessage = it.message) }
        _uiState.value = _uiState.value.copy(isRefreshing = false)
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
