package com.nerve.android.ui.nodes

import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.transport.ServerConfig

internal fun groupNodesByServer(
    items: List<NodeItemUi>,
    servers: List<ServerConfig>,
): List<Pair<ServerConfig, List<NodeItemUi>>> {
    val byServerId = items.groupBy { it.serverId }
    val knownServerIds = servers.map { it.id }.toSet()
    val orderedGroups = servers.mapNotNull { server ->
        val serverNodes = byServerId[server.id] ?: return@mapNotNull null
        if (serverNodes.isEmpty()) return@mapNotNull null
        server to serverNodes.sortedBy { it.nodeName }
    }
    // Fallback: nodes whose serverId isn't in the servers list still get rendered,
    // grouped under a synthetic ServerConfig built from the item's own serverName.
    // Keeps the screen non-blank during transient states (servers list still loading)
    // and preserves backward compatibility for callers that don't supply a servers list.
    val unknownGroups = byServerId
        .filterKeys { it !in knownServerIds }
        .map { (serverId, nodes) ->
            val displayName = nodes.first().serverName
            ServerConfig(id = serverId, name = displayName, address = "") to nodes.sortedBy { it.nodeName }
        }
    return orderedGroups + unknownGroups
}
