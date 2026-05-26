package com.nerve.android.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nerve.android.NerveApp
import com.nerve.android.lifelog.ui.LifeLogScreen
import com.nerve.android.lifelog.ui.LifeLogViewModel
import com.nerve.android.morning.ui.MorningBriefScreen
import com.nerve.android.morning.ui.MorningBriefViewModel
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
    val lifeLogViewModel = remember(app) { LifeLogViewModel(app) }
    val morningBriefViewModel = remember(app) { MorningBriefViewModel(app) }
    
    val serverState by serverViewModel.uiState.collectAsState()
    val channelsState by channelsViewModel.uiState.collectAsState()
    val servers by app.serverRegistry.servers.collectAsState()
    val nodes by app.serverRegistry.nodes.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val dismissedVersion by updateViewModel.dismissedVersionCode.collectAsState()
    val downloadState by updateViewModel.download.collectAsState()
    val scope = rememberCoroutineScope()
    val nav = remember { AppNavigation() }
    val nodesGridState = rememberLazyStaggeredGridState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Periodic check and check on Resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            updateViewModel.refresh()
            kotlinx.coroutines.delay(60 * 1000) // 1 minute
        }
    }

    LaunchedEffect(nav.screen, servers, nodes) {
        nav.guardChat(servers, nodes)
    }

    LaunchedEffect(app.shouldOpenMorningBrief) {
        if (app.shouldOpenMorningBrief) {
            nav.selectTab(3)
            app.shouldOpenMorningBrief = false
        }
    }

    NerveTheme(darkTheme = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = nav.screen) {
                    AppScreen.Main -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Main Content with Fade
                                Box(modifier = Modifier.weight(1f)) {
                                    AnimatedContent(
                                        targetState = nav.selectedTab,
                                        label = "tab_fade",
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                                        }
                                    ) { tab ->
                                        when (tab) {
                                            0 -> NodesRoute(
                                                viewModel = nodesViewModel,
                                                serverRegistry = app.serverRegistry,
                                                gridState = nodesGridState,
                                                onOpenChat = { s, n, name -> nav.openChat(s, n, name) },
                                                onOpenServers = { nav.openServers() },
                                            )
                                            1 -> ChannelsRoute(
                                                viewModel = channelsViewModel,
                                                onOpenChannel = { s, c, name -> nav.openChannelChat(s, c, name) },
                                            )
                                            2 -> LifeLogScreen(vm = lifeLogViewModel)
                                            3 -> MorningBriefScreen(vm = morningBriefViewModel)
                                        }
                                    }
                                }
                            }

                            // Update Banner - Floating Overlay
                            AnimatedVisibility(
                                visible = (updateState as? UpdateState.Available)?.let { it.info.versionCode != dismissedVersion } ?: false,
                                enter = slideInVertically { -it } + fadeIn(),
                                exit = slideOutVertically { -it } + fadeOut(),
                                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                            ) {
                                val available = updateState as? UpdateState.Available
                                if (available != null) {
                                    UpdateBanner(
                                        info = available.info,
                                        download = downloadState,
                                        onUpdate = {
                                            when (val state = downloadState) {
                                                is DownloadState.Ready -> ApkInstaller.launchInstall(context, state.file)
                                                else -> updateViewModel.startDownload()
                                            }
                                        },
                                        onDismiss = {
                                            updateViewModel.resetDownload()
                                            updateViewModel.dismiss()
                                        },
                                        onRetry = { updateViewModel.startDownload() },
                                    )
                                }
                            }

                            // Floating Navigation Bar
                            FloatingPillNavigationBar(
                                selectedTab = nav.selectedTab,
                                onTabSelected = { nav.selectTab(it) },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            )
                        }
                    }

                    is AppScreen.Servers -> {
                        BackHandler { nav.back() }
                        ServersScreen(
                            state = serverState,
                            isDarkTheme = false,
                            onToggleTheme = { /* Logic removed for Warm Light vision */ },
                            onRefresh = { scope.launch { serverViewModel.refresh() } },
                            onRefreshServer = { scope.launch { serverViewModel.refreshServer(it) } },
                            onAddServer = { id, name, addr -> scope.launch { serverViewModel.addServer(id, name, addr) } },
                            onRemoveServer = { scope.launch { serverViewModel.removeServer(it) } },
                            onReorder = { scope.launch { serverViewModel.reorderServers(it) } },
                            onBack = { nav.back() },
                            onCheckUpdate = { updateViewModel.refresh() },
                            versionName = com.nerve.android.BuildConfig.VERSION_NAME,
                        )
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
                            onOpenDm = { s, n, name -> nav.openChat(s, n, name) },
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
}
