package com.nerve.android.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nerve.android.presentation.server.ServerUiState
import com.nerve.android.transport.ConnectionState

@Composable
fun ServersScreen(
    state: ServerUiState,
    onAddServer: (id: String, name: String, address: String) -> Unit,
    onRemoveServer: (serverId: String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(
                    items = state.servers,
                    key = { it.id },
                ) { server ->
                    val connection = state.connections.firstOrNull { it.serverId == server.id }?.state
                        ?: ConnectionState.DISCONNECTED
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Status dot
                            Surface(
                                shape = CircleShape,
                                color = if (connection == ConnectionState.CONNECTED)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(10.dp),
                            ) {}
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            ) {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    server.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            IconButton(onClick = { onRemoveServer(server.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${server.name}")
                            }
                        }
                    }
                }
            }
            SmallFloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add server")
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
