package com.nerve.android.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerve.android.presentation.server.ServerUiState
import com.nerve.android.transport.ConnectionState

@Composable
fun ServerSheet(
    state: ServerUiState,
    onAddServer: (id: String, name: String, address: String) -> Unit,
    onRemoveServer: (serverId: String) -> Unit,
    onDismiss: () -> Unit,
    initialShowAddDialog: Boolean = false,
    onShowAddDialogChange: ((Boolean) -> Unit)? = null,
) {
    var internalShowAdd by remember { mutableStateOf(initialShowAddDialog) }
    val showAdd = onShowAddDialogChange?.let { initialShowAddDialog } ?: internalShowAdd
    val setShowAdd: (Boolean) -> Unit = { value ->
        if (onShowAddDialogChange != null) {
            onShowAddDialogChange(value)
        } else {
            internalShowAdd = value
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server sheet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                state.servers.forEach { server ->
                    val connection = state.connections.firstOrNull { it.serverId == server.id }?.state
                        ?: ConnectionState.DISCONNECTED
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(server.name)
                        Text(connection.name, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onRemoveServer(server.id) }) {
                            Text("Remove ${server.name}")
                        }
                    }
                }
                Button(onClick = { setShowAdd(true) }) {
                    Text("Add server")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )

    if (showAdd) {
        AddServerDialog(
            isSubmitting = state.isSubmitting,
            onAddServer = onAddServer,
            onDismiss = { setShowAdd(false) },
        )
    }
}

@Composable
private fun AddServerDialog(
    isSubmitting: Boolean,
    onAddServer: (id: String, name: String, address: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val canSubmit = id.isNotBlank() && name.isNotBlank() && address.isNotBlank() && !isSubmitting

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Id") })
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAddServer(id, name, address)
                    onDismiss()
                },
                enabled = canSubmit,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
