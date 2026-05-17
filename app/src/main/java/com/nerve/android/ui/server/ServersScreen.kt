package com.nerve.android.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.presentation.server.ServerUiState
import com.nerve.android.transport.ConnectionState
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.StatusError
import com.nerve.android.ui.theme.StatusIdle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    versionName: String,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh all")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add server")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            
            // --- NEW: App Update Section ---
            Text(
                text = "Maintenance",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Current Version", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("v$versionName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onCheckUpdate,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                    ) {
                        Icon(Icons.Default.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check Now", fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Primary Infrastructure",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            state.errorMessage?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.servers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { showAddDialog = true }) { Text("Register First Server") }
                }
            } else {
                var workingIds by remember { mutableStateOf(state.servers.map { it.id }) }
                LaunchedEffect(state.servers) { workingIds = state.servers.map { it.id } }

                val lazyListState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    workingIds = workingIds.toMutableList().apply { add(to.index, removeAt(from.index)) }
                }

                LaunchedEffect(reorderableState.isAnyItemDragging) {
                    if (!reorderableState.isAnyItemDragging && workingIds != state.servers.map { it.id }) {
                        onReorder(workingIds)
                    }
                }

                val orderedServers = workingIds.mapNotNull { id -> state.servers.firstOrNull { it.id == id } }

                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(orderedServers, key = { it.id }) { server ->
                        ReorderableItem(reorderableState, key = server.id) { isDragging ->
                            val connection = state.connections.firstOrNull { it.serverId == server.id }?.state ?: ConnectionState.DISCONNECTED
                            
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = if (isDragging) 8.dp else 1.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth().longPressDraggableHandle()
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (connection == ConnectionState.CONNECTED) StatusIdle else StatusError,
                                        modifier = Modifier.size(10.dp)
                                    ) {}
                                    
                                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                                        Text(server.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text(server.address, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    IconButton(onClick = { onRefreshServer(server.id) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { onRemoveServer(server.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = StatusError, modifier = Modifier.size(20.dp))
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
        title = { Text("Register Server", fontWeight = FontWeight.Black) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("WS Endpoint") },
                    placeholder = { Text("100.x.x.x:4800") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(address, name) }, enabled = canSubmit, shape = RoundedCornerShape(12.dp)) {
                Text("Register")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
