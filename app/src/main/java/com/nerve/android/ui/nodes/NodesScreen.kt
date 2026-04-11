package com.nerve.android.ui.nodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.presentation.nodes.NodesUiState
import com.nerve.android.transport.ServerConfig

@Composable
fun NodesScreen(
    state: NodesUiState,
    servers: List<ServerConfig> = emptyList(),
    onRefresh: () -> Unit,
    onOpenChat: (String, String, String) -> Unit,
    onStop: (String, String) -> Unit,
    onSpawn: (String, String, String?, String?) -> Unit,
    initialShowSpawnDialog: Boolean = false,
    onShowSpawnDialogChange: ((Boolean) -> Unit)? = null,
) {
    var internalShowSpawn by remember { mutableStateOf(initialShowSpawnDialog) }
    val showSpawn = onShowSpawnDialogChange?.let { initialShowSpawnDialog } ?: internalShowSpawn
    val setShowSpawn: (Boolean) -> Unit = { value ->
        if (onShowSpawnDialogChange != null) {
            onShowSpawnDialogChange(value)
        } else {
            internalShowSpawn = value
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Top bar: title + badge + actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Agents", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.totalCount > 0) {
                Text(
                    "${state.connectedCount}/${state.totalCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.connectedCount == state.totalCount)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = { setShowSpawn(true) }) { Text("+") }
            Button(onClick = onRefresh, enabled = !state.isRefreshing) { Text("Refresh") }
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (state.items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No agents", style = MaterialTheme.typography.bodyLarge)
                Text("Tap + to spawn one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val grouped = state.items
                .sortedWith(compareBy<NodeItemUi> { it.serverName }.thenBy { it.nodeName })
                .groupBy { it.serverName }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                grouped.forEach { (serverName, nodes) ->
                    item(key = "header:$serverName") {
                        Text(
                            text = serverName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    items(items = nodes, key = { "${it.serverId}:${it.nodeId}" }) { item ->
                        NodeRow(
                            item = item,
                            onOpenChat = onOpenChat,
                            onStop = onStop,
                        )
                    }
                }
            }
        }
    }

    if (showSpawn) {
        SpawnNodeDialog(
            servers = servers,
            onSpawn = onSpawn,
            onDismiss = { setShowSpawn(false) },
        )
    }
}

@Composable
private fun NodeRow(
    item: NodeItemUi,
    onOpenChat: (String, String, String) -> Unit,
    onStop: (String, String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenChat(item.serverId, item.nodeId, item.nodeName) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status dot
        Surface(
            shape = CircleShape,
            color = statusColor(item.status),
            modifier = Modifier.size(10.dp),
        ) {}

        // Name + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(item.nodeName, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.cwdLabel?.let {
                    Text(
                        "· $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Stop button (only if not already stopped)
        if (item.status != "stopped") {
            IconButton(onClick = { onStop(item.serverId, item.nodeId) }) {
                Text("×", style = MaterialTheme.typography.titleMedium)
            }
        }

        // Short id
        Text(
            item.shortId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun statusColor(status: String): Color = when (status) {
    "idle" -> Color(0xFF4CAF50)
    "busy" -> Color(0xFFFFC107)
    "error" -> Color(0xFFF44336)
    "stopped" -> Color(0xFF9E9E9E)
    else -> Color(0xFF9E9E9E)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpawnNodeDialog(
    servers: List<ServerConfig>,
    onSpawn: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedServer by remember { mutableStateOf(servers.firstOrNull()) }
    var adapter by remember { mutableStateOf("claude") }
    var expanded by remember { mutableStateOf(false) }
    val canSubmit = selectedServer != null && adapter.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spawn agent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (servers.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedServer?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Server") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            servers.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name) },
                                    onClick = {
                                        selectedServer = server
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                } else if (servers.size == 1) {
                    Text("Server: ${servers.first().name}", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedTextField(
                    value = adapter,
                    onValueChange = { adapter = it },
                    label = { Text("Adapter") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedServer?.let { onSpawn(it.id, adapter, null, null) }
                    onDismiss()
                },
                enabled = canSubmit,
            ) {
                Text("Spawn")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
