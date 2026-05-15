package com.nerve.android.ui.nodes

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
    onOpenServers: () -> Unit,
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
                    IconButton(onClick = onOpenServers) {
                        Icon(Icons.Default.Dns, contentDescription = "Servers")
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
        floatingActionButton = {
            FloatingActionButton(onClick = { setShowSpawn(true) }) {
                Icon(Icons.Default.Add, contentDescription = "Spawn agent")
            }
        }
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
                val grouped = groupNodesByServer(state.items, servers)

                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    grouped.forEach { (server, nodes) ->
                        item(key = "header:${server.id}", span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                text = server.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(items = nodes, key = { "${it.serverId}:${it.nodeId}" }) { item ->
                            NodeCard(
                                item = item,
                                onOpenChat = onOpenChat,
                                onStop = onStop,
                            )
                        }
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(80.dp))
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
private fun NodeCard(
    item: NodeItemUi,
    onOpenChat: (String, String, String) -> Unit,
    onStop: (String, String) -> Unit,
) {
    var showStopConfirm by remember { mutableStateOf(false) }
    val isBusy = item.status == "busy"
    
    val infiniteTransition = rememberInfiniteTransition(label = "node_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isBusy) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

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

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenChat(item.serverId, item.nodeId, item.nodeName) },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.nodeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                if (item.status != "stopped") {
                    IconButton(
                        onClick = { showStopConfirm = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Stop ${item.nodeName}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(12.dp)
                ) {
                    if (isBusy) {
                        Surface(
                            shape = CircleShape,
                            color = statusColor(item.status).copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(12.dp)
                                .scale(pulseScale)
                        ) {}
                    }
                    Surface(
                        shape = CircleShape,
                        color = statusColor(item.status),
                        modifier = Modifier.size(8.dp),
                    ) {}
                }
                
                Text(
                    text = item.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(item.status),
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = item.shortId,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                
                item.cwdLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
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