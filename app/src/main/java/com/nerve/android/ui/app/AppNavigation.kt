package com.nerve.android.ui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import com.nerve.android.domain.server.ServerNode
import com.nerve.android.transport.ServerConfig

sealed interface AppScreen {
    data object Main : AppScreen
    data object Servers : AppScreen
    data class Chat(val serverId: String, val nodeId: String, val nodeName: String) : AppScreen
    data class ChannelChat(val serverId: String, val channelId: String, val channelName: String) : AppScreen
}

class AppNavigation(
    initialScreen: AppScreen = AppScreen.Main,
    initialSelectedTab: Int = 0,
) {
    var screen: AppScreen by mutableStateOf(initialScreen)
        private set
    var selectedTab: Int by mutableIntStateOf(initialSelectedTab.coerceIn(0, 3))
        private set
    var transientError: String? by mutableStateOf(null)
        private set

    fun selectTab(index: Int) {
        if (index !in 0..3) return
        selectedTab = index
        screen = AppScreen.Main
    }

    fun openServers() {
        transientError = null
        screen = AppScreen.Servers
    }

    fun openChat(serverId: String, nodeId: String, nodeName: String) {
        transientError = null
        screen = AppScreen.Chat(serverId, nodeId, nodeName)
    }

    fun openChannelChat(serverId: String, channelId: String, channelName: String) {
        transientError = null
        screen = AppScreen.ChannelChat(serverId, channelId, channelName)
    }

    fun back() {
        if (screen !is AppScreen.Main) {
            screen = AppScreen.Main
        }
    }

    fun showError(message: String) {
        transientError = message
    }

    fun dismissError() {
        transientError = null
    }

    fun guardChat(servers: List<ServerConfig>, nodes: List<ServerNode>) {
        val current = screen
        if (current !is AppScreen.Chat) return
        val serverExists = servers.any { it.id == current.serverId }
        if (!serverExists) {
            back()
            showError("Current server is unavailable")
            return
        }
        val currentServerNodes = nodes.filter { it.serverId == current.serverId }
        if (currentServerNodes.isEmpty()) return
        val exactNode = currentServerNodes.firstOrNull { it.node.id == current.nodeId }
        if (exactNode != null) return
        val sameNameNode = currentServerNodes.firstOrNull { it.node.name == current.nodeName }
        if (sameNameNode != null) {
            screen = AppScreen.Chat(current.serverId, sameNameNode.node.id, sameNameNode.node.name)
            return
        }
        back()
    }

    fun toSavedState(): List<String> {
        val tab = selectedTab.toString()
        return when (val current = screen) {
            AppScreen.Main -> listOf(SCREEN_MAIN, tab)
            AppScreen.Servers -> listOf(SCREEN_SERVERS, tab)
            is AppScreen.Chat -> listOf(SCREEN_CHAT, tab, current.serverId, current.nodeId, current.nodeName)
            is AppScreen.ChannelChat -> listOf(
                SCREEN_CHANNEL_CHAT,
                tab,
                current.serverId,
                current.channelId,
                current.channelName,
            )
        }
    }

    companion object {
        private const val SCREEN_MAIN = "main"
        private const val SCREEN_SERVERS = "servers"
        private const val SCREEN_CHAT = "chat"
        private const val SCREEN_CHANNEL_CHAT = "channel_chat"

        fun fromSavedState(saved: List<String>): AppNavigation {
            val tab = saved.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 3) ?: 0
            val screen = when (saved.firstOrNull()) {
                SCREEN_SERVERS -> AppScreen.Servers
                SCREEN_CHAT -> {
                    val serverId = saved.getOrNull(2)
                    val nodeId = saved.getOrNull(3)
                    val nodeName = saved.getOrNull(4)
                    if (serverId != null && nodeId != null && nodeName != null) {
                        AppScreen.Chat(serverId, nodeId, nodeName)
                    } else {
                        AppScreen.Main
                    }
                }
                SCREEN_CHANNEL_CHAT -> {
                    val serverId = saved.getOrNull(2)
                    val channelId = saved.getOrNull(3)
                    val channelName = saved.getOrNull(4)
                    if (serverId != null && channelId != null && channelName != null) {
                        AppScreen.ChannelChat(serverId, channelId, channelName)
                    } else {
                        AppScreen.Main
                    }
                }
                else -> AppScreen.Main
            }
            return AppNavigation(initialScreen = screen, initialSelectedTab = tab)
        }
    }
}

val AppNavigationSaver: Saver<AppNavigation, List<String>> = Saver(
    save = { it.toSavedState() },
    restore = { AppNavigation.fromSavedState(it) },
)
