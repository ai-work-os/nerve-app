package com.nerve.android.ui.nodes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.presentation.nodes.NodesViewModel
import kotlinx.coroutines.launch

@Composable
fun NodesRoute(
    viewModel: NodesViewModel,
    serverRegistry: ServerRegistry,
    onOpenChat: (String, String, String) -> Unit,
    onOpenServers: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val servers by serverRegistry.servers.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel, serverRegistry) {
        viewModel.refresh()
    }

    NodesScreen(
        state = state,
        servers = servers,
        onRefresh = {
            scope.launch {
                viewModel.refresh()
            }
        },
        onOpenChat = onOpenChat,
        onOpenServers = onOpenServers,
        onStop = { serverId, nodeId ->
            scope.launch {
                viewModel.stopNode(serverId, nodeId)
            }
        },
        onSpawn = { serverId, adapter, name, cwd ->
            scope.launch {
                viewModel.spawnNode(serverId, adapter, name, cwd)
            }
        },
    )
}
