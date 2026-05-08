package com.nerve.android.ui.nodes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.presentation.nodes.NodesUiState
import com.nerve.android.transport.ServerConfig
import com.nerve.android.ui.theme.StatusError
import com.nerve.android.ui.theme.StatusIdle
import com.nerve.android.ui.theme.statusColor

internal object SpawnDialogDefaults {
    const val ADAPTER = "codex"
}

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Agents", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        if (state.totalCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (state.connectedCount > 0) StatusIdle.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "${state.connectedCount}/${state.totalCount}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = if (state.connectedCount > 0) StatusIdle else StatusError,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { setShowSpawn(true) }) {
                        Icon(Icons.Default.Add, contentDescription = "Spawn agent")
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No agents", style = MaterialTheme.typography.bodyLarge)
                    Text("Tap + to spawn one", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val grouped = state.items
                    .sortedWith(compareBy<NodeItemUi> { it.serverName }.thenBy { it.nodeName })
                    .groupBy { it.serverName }

                LazyColumn {
                    grouped.forEach { (serverName, nodes) ->
                        item(key = "header:$serverName") {
                            Text(
                                text = serverName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(items = nodes, key = { "${it.serverId}:${it.nodeId}" }) { item ->
                            NodeRow(
                                item = item,
                                onOpenChat = onOpenChat,
                                onStop = onStop,
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 38.dp),
                            )
                        }
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
    var showStopConfirm by remember { mutableStateOf(false) }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("停止节点") },
            text = { Text("确定要停止 ${item.nodeName} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    onStop(item.serverId, item.nodeId)
                }) { Text("停止", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("取消") }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenChat(item.serverId, item.nodeId, item.nodeName) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Text(
                item.nodeName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(item.status),
                )
                item.cwdLabel?.let {
                    Text(
                        " · $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Stop button
        if (item.status != "stopped") {
            IconButton(
                onClick = { showStopConfirm = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Stop ${item.nodeName}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Short id
        Text(
            item.shortId,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpawnNodeDialog(
    servers: List<ServerConfig>,
    onSpawn: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedServer by remember { mutableStateOf(servers.firstOrNull()) }
    var adapter by remember { mutableStateOf(SpawnDialogDefaults.ADAPTER) }
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
