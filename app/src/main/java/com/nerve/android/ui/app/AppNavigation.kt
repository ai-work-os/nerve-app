package com.nerve.android.ui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nerve.android.domain.server.ServerNode
import com.nerve.android.transport.ServerConfig

sealed interface AppScreen {
    data object Main : AppScreen
    data class Chat(val serverId: String, val nodeId: String, val nodeName: String) : AppScreen
    data class ChannelChat(val serverId: String, val channelId: String, val channelName: String) : AppScreen
}

class AppNavigation {
    var screen: AppScreen by mutableStateOf(AppScreen.Main)
        private set
    var selectedTab: Int by mutableIntStateOf(0)
        private set
    var transientError: String? by mutableStateOf(null)
        private set

    fun selectTab(index: Int) {
        if (index !in 0..2) return
        selectedTab = index
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
}
