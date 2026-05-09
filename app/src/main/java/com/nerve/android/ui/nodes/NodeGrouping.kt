package com.nerve.android.ui.nodes

import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.transport.ServerConfig

internal fun groupNodesByServer(
    items: List<NodeItemUi>,
    servers: List<ServerConfig>,
): List<Pair<ServerConfig, List<NodeItemUi>>> {
    val byServerId = items.groupBy { it.serverId }
    return servers.mapNotNull { server ->
        val serverNodes = byServerId[server.id] ?: return@mapNotNull null
        if (serverNodes.isEmpty()) return@mapNotNull null
        server to serverNodes.sortedBy { it.nodeName }
    }
}
