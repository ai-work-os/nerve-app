package com.nerve.android.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.nerve.android.NerveApp
import com.nerve.android.ui.channels.ChannelChatRoute
import com.nerve.android.ui.channels.ChannelsRoute
import com.nerve.android.ui.chat.ChatRoute
import com.nerve.android.ui.nodes.NodesRoute
import com.nerve.android.ui.server.ServersScreen
import com.nerve.android.ui.theme.NerveTheme
import com.nerve.android.update.ApkInstaller
import com.nerve.android.update.DownloadState
import com.nerve.android.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun AppRoute(app: NerveApp) {
    val nodesViewModel = remember(app) { app.createNodesViewModel() }
    val serverViewModel = remember(app) { app.createServerViewModel() }
    val chatViewModel = remember(app) { app.createChatViewModel() }
    val channelsViewModel = remember(app) { app.createChannelsViewModel() }
    val updateViewModel = remember(app) { app.createUpdateViewModel() }
    val serverState by serverViewModel.uiState.collectAsState()
    val channelsState by channelsViewModel.uiState.collectAsState()
    val servers by app.serverRegistry.servers.collectAsState()
    val nodes by app.serverRegistry.nodes.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val dismissedVersion by updateViewModel.dismissedVersionCode.collectAsState()
    val downloadState by updateViewModel.download.collectAsState()
    val scope = rememberCoroutineScope()
    val nav = remember { AppNavigation() }
    val systemDark = isSystemInDarkTheme()
    var darkTheme by remember { mutableStateOf(systemDark) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        updateViewModel.refresh()
    }

    // Guard: if current chat target disappears, go back to main
    LaunchedEffect(nav.screen, servers, nodes) {
        nav.guardChat(servers, nodes)
    }

    NerveTheme(darkTheme = darkTheme) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            when (val current = nav.screen) {
                AppScreen.Main -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        (updateState as? UpdateState.Available)
                            ?.takeIf { it.info.versionCode != dismissedVersion }
                            ?.let { available ->
                                UpdateBanner(
                                    info = available.info,
                                    download = downloadState,
                                    onUpdate = {
                                        when (val current = downloadState) {
                                            is DownloadState.Ready -> {
                                                ApkInstaller.launchInstall(context, current.file)
                                            }
                                            DownloadState.Idle, is DownloadState.Failed,
                                            is DownloadState.InProgress -> {
                                                updateViewModel.startDownload()
                                            }
                                        }
                                    },
                                    onDismiss = {
                                        updateViewModel.resetDownload()
                                        updateViewModel.dismiss()
                                    },
                                    onRetry = { updateViewModel.startDownload() },
                                )
                            }

                        if (downloadState is DownloadState.Ready) {
                            LaunchedEffect(downloadState) {
                                val ready = downloadState as DownloadState.Ready
                                ApkInstaller.launchInstall(context, ready.file)
                            }
                        }

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
                                    isDarkTheme = darkTheme,
                                    onToggleTheme = { darkTheme = !darkTheme },
                                    onRefresh = { scope.launch { serverViewModel.refresh() } },
                                    onRefreshServer = { serverId ->
                                        scope.launch { serverViewModel.refreshServer(serverId) }
                                    },
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
                                icon = { Icon(Icons.Default.SmartToy, contentDescription = "Agents") },
                                label = { Text("Agents") },
                                colors = navColors,
                            )
                            NavigationBarItem(
                                selected = nav.selectedTab == 1,
                                onClick = { nav.selectTab(1) },
                                icon = { Icon(Icons.Default.Forum, contentDescription = "Channels") },
                                label = { Text("Channels") },
                                colors = navColors,
                            )
                            NavigationBarItem(
                                selected = nav.selectedTab == 2,
                                onClick = { nav.selectTab(2) },
                                icon = { Icon(Icons.Default.Dns, contentDescription = "Servers") },
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
                        onOpenDm = { serverId, nodeId, nodeName ->
                            nav.openChat(serverId, nodeId, nodeName)
                        },
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
