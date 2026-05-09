package com.nerve.android.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.presentation.server.ServerUiState
import com.nerve.android.transport.ConnectionState
import com.nerve.android.ui.theme.StatusError
import com.nerve.android.ui.theme.StatusIdle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    state: ServerUiState,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshServer: (serverId: String) -> Unit,
    onAddServer: (id: String, name: String, address: String) -> Unit,
    onRemoveServer: (serverId: String) -> Unit,
    onReorder: (orderedIds: List<String>) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servers", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle theme",
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh servers")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add server")
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

            if (state.servers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No servers configured", style = MaterialTheme.typography.bodyLarge)
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text("Add Server")
                        }
                    }
                }
            } else {
                val lazyListState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    val ids = state.servers.map { it.id }.toMutableList()
                    val moved = ids.removeAt(from.index)
                    ids.add(to.index, moved)
                    onReorder(ids)
                }

                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.servers,
                        key = { it.id },
                    ) { server ->
                        ReorderableItem(reorderableState, key = server.id) { isDragging ->
                            val connection = state.connections.firstOrNull { it.serverId == server.id }?.state
                                ?: ConnectionState.DISCONNECTED
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = if (isDragging) 8.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .longPressDraggableHandle(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (connection == ConnectionState.CONNECTED) StatusIdle else StatusError,
                                        modifier = Modifier.size(10.dp),
                                    ) {}
                                    Column(
                                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                                    ) {
                                        Text(
                                            server.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                        )
                                        Text(
                                            server.address,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                    val index = state.servers.indexOfFirst { it.id == server.id }
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val ids = state.servers.map { it.id }.toMutableList()
                                                val tmp = ids.removeAt(index)
                                                ids.add(index - 1, tmp)
                                                onReorder(ids)
                                            }
                                        },
                                        enabled = index > 0,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move ${server.name} up",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index in 0 until state.servers.lastIndex) {
                                                val ids = state.servers.map { it.id }.toMutableList()
                                                val tmp = ids.removeAt(index)
                                                ids.add(index + 1, tmp)
                                                onReorder(ids)
                                            }
                                        },
                                        enabled = index >= 0 && index < state.servers.lastIndex,
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move ${server.name} down",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRefreshServer(server.id) },
                                        enabled = !state.isRefreshing,
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Refresh ${server.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { onRemoveServer(server.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove ${server.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            isSubmitting = state.isSubmitting,
            onAdd = { address, name ->
                val id = address.replace(":", "-").replace(".", "-")
                onAddServer(id, name.ifBlank { address }, address)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun AddServerDialog(
    isSubmitting: Boolean,
    onAdd: (address: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val canSubmit = address.isNotBlank() && !isSubmitting

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (host:port)") },
                    placeholder = { Text("192.168.1.100:4800") },
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(address, name) }, enabled = canSubmit) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
