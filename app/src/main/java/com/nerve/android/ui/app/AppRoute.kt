package com.nerve.android.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerve.android.NerveApp
import com.nerve.android.ui.channels.ChannelChatRoute
import com.nerve.android.ui.channels.ChannelsRoute
import com.nerve.android.ui.chat.ChatRoute
import com.nerve.android.ui.nodes.NodesRoute
import com.nerve.android.ui.server.ServersScreen
import com.nerve.android.ui.theme.NerveTheme
import kotlinx.coroutines.launch

@Composable
fun AppRoute(app: NerveApp) {
    val nodesViewModel = remember(app) { app.createNodesViewModel() }
    val serverViewModel = remember(app) { app.createServerViewModel() }
    val chatViewModel = remember(app) { app.createChatViewModel() }
    val channelsViewModel = remember(app) { app.createChannelsViewModel() }
    val serverState by serverViewModel.uiState.collectAsState()
    val channelsState by channelsViewModel.uiState.collectAsState()
    val servers by app.serverRegistry.servers.collectAsState()
    val nodes by app.serverRegistry.nodes.collectAsState()
    val scope = rememberCoroutineScope()
    val nav = remember { AppNavigation() }

    // Guard: if current chat target disappears, go back to main
    LaunchedEffect(nav.screen, servers, nodes) {
        val current = nav.screen
        if (current is AppScreen.Chat) {
            val serverExists = servers.any { it.id == current.serverId }
            val nodeExists = nodes.any { it.serverId == current.serverId && it.node.id == current.nodeId }
            if (!serverExists || !nodeExists) {
                nav.back()
                nav.showError("Current chat target is unavailable")
            }
        }
    }

    NerveTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val current = nav.screen) {
                AppScreen.Main -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Transient error banner
                        nav.transientError?.let { error ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { nav.dismissError() }) {
                                    Text("Close")
                                }
                            }
                        }

                        // Tab content
                        Box(modifier = Modifier.weight(1f)) {
                            when (nav.selectedTab) {
                                0 -> NodesRoute(
                                    viewModel = nodesViewModel,
                                    serverRegistry = app.serverRegistry,
                                    onOpenChat = { serverId, nodeId, nodeName ->
                                        nav.openChat(serverId, nodeId, nodeName)
                                    },
                                )
                                1 -> ChannelsRoute(
                                    viewModel = channelsViewModel,
                                    onOpenChannel = { serverId, channelId, channelName ->
                                        nav.openChannelChat(serverId, channelId, channelName)
                                    },
                                )
                                2 -> ServersScreen(
                                    state = serverState,
                                    onAddServer = { id, name, address ->
                                        scope.launch { serverViewModel.addServer(id, name, address) }
                                    },
                                    onRemoveServer = { serverId ->
                                        scope.launch { serverViewModel.removeServer(serverId) }
                                    },
                                )
                            }
                        }

                        // Bottom navigation bar
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            NavigationBarItem(
                                selected = nav.selectedTab == 0,
                                onClick = { nav.selectTab(0) },
                                icon = { Icon(Icons.Default.Person, contentDescription = "Agents") },
                                label = { Text("Agents") },
                                colors = navColors,
                            )
                            NavigationBarItem(
                                selected = nav.selectedTab == 1,
                                onClick = { nav.selectTab(1) },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Channels") },
                                label = { Text("Channels") },
                                colors = navColors,
                            )
                            NavigationBarItem(
                                selected = nav.selectedTab == 2,
                                onClick = { nav.selectTab(2) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Servers") },
                                label = { Text("Servers") },
                                colors = navColors,
                            )
                        }
                    }
                }

                is AppScreen.Chat -> {
                    BackHandler { nav.back() }
                    ChatRoute(
                        viewModel = chatViewModel,
                        serverRegistry = app.serverRegistry,
                        serverId = current.serverId,
                        nodeId = current.nodeId,
                        nodeName = current.nodeName,
                        onBack = { nav.back() },
                    )
                }

                is AppScreen.ChannelChat -> {
                    BackHandler { nav.back() }
                    ChannelChatRoute(
                        viewModel = channelsViewModel,
                        serverId = current.serverId,
                        channelId = current.channelId,
                        channelName = current.channelName,
                        onBack = { nav.back() },
                    )
                }
            }
        }
    }
}
