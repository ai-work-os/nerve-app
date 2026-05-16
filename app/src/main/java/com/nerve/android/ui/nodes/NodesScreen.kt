package com.nerve.android.ui.nodes

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.presentation.nodes.NodesUiState
import com.nerve.android.transport.ServerConfig
import com.nerve.android.ui.theme.AmberPrimary
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
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { setShowSpawn(true) },
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = AmberPrimary,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(bottom = 80.dp) // Adjust for pill nav
            ) {
                Icon(Icons.Default.Add, contentDescription = "Spawn agent", modifier = Modifier.size(32.dp))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            
            // Hero Title Section
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "Hub",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = if (state.connectedCount > 0) StatusIdle else StatusError,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.connectedCount > 0) "All systems operational" else "Connection unstable",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // CRITICAL: Manage Servers Entry Point
                IconButton(
                    onClick = onOpenServers,
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Settings, contentDescription = "Manage Servers", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            state.errorMessage?.let {
                Text(
                    it, 
                    color = MaterialTheme.colorScheme.error, 
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }

            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No agents deployed", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val grouped = groupNodesByServer(state.items, servers)

                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    grouped.forEach { (server, nodes) ->
                        item(key = "header:${server.id}", span = StaggeredGridItemSpan.FullLine) {
                            Text(
                                text = server.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
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
        targetValue = if (isBusy) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Terminate Node", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to stop ${item.nodeName}?") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    onStop(item.serverId, item.nodeId)
                }) { Text("Terminate", color = StatusError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Surface(
        onClick = { onOpenChat(item.serverId, item.nodeId, item.nodeName) },
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Large Initial Watermark
            Text(
                text = item.nodeName.take(1).uppercase(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Black,
                color = AmberPrimary.copy(alpha = 0.05f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 20.dp)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Avatar Box
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isBusy) AmberPrimary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.nodeName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isBusy) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ID Badge
                    Surface(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = item.shortId,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = item.nodeName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
                        if (isBusy) {
                            Surface(
                                shape = CircleShape,
                                color = statusColor(item.status).copy(alpha = 0.2f),
                                modifier = Modifier.size(12.dp).scale(pulseScale)
                            ) {}
                        }
                        Surface(
                            shape = CircleShape,
                            color = statusColor(item.status),
                            modifier = Modifier.size(6.dp)
                        ) {}
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBusy) "Thinking..." else item.status.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor(item.status),
                        fontStyle = if (isBusy) FontStyle.Italic else FontStyle.Normal
                    )
                }

                if (item.cwdLabel != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = item.cwdLabel!!,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(0.6f)
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
        title = { Text("Spawn Agent", fontWeight = FontWeight.Black) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            servers.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        selectedServer = server
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = adapter,
                    onValueChange = { adapter = it },
                    label = { Text("Adapter") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
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
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Spawn")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}