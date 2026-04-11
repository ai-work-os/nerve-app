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
        if (nodes.isEmpty()) return
        val serverExists = servers.any { it.id == current.serverId }
        val nodeExists = nodes.any { it.serverId == current.serverId && it.node.id == current.nodeId }
        if (!serverExists || !nodeExists) {
            back()
            showError("Current chat target is unavailable")
        }
    }
}
