package com.nerve.android.ui.nodes

import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.transport.ServerConfig

internal data class NodeGroup(
    val title: String,
    val isSpecial: Boolean = false,
    val nodes: List<NodeItemUi>
)

internal fun groupNodesByServer(
    items: List<NodeItemUi>,
    servers: List<ServerConfig>,
): List<NodeGroup> {
    val activeNodes = items.filter { it.status == "busy" }.sortedBy { it.nodeName }
    val otherNodes = items.filter { it.status != "busy" }
    
    val groups = mutableListOf<NodeGroup>()
    
    // 1. Top Section: Active Agents
    if (activeNodes.isNotEmpty()) {
        groups.add(NodeGroup(title = "Active Sessions", isSpecial = true, nodes = activeNodes))
    }
    
    // 2. Main Sections: Servers
    val byServerId = otherNodes.groupBy { it.serverId }
    val knownServerIds = servers.map { it.id }.toSet()
    
    servers.forEach { server ->
        val serverNodes = byServerId[server.id] ?: return@forEach
        if (serverNodes.isNotEmpty()) {
            groups.add(NodeGroup(title = server.name, nodes = serverNodes.sortedBy { it.nodeName }))
        }
    }
    
    // 3. Fallback: Unknown Servers
    byServerId
        .filterKeys { it !in knownServerIds }
        .forEach { (serverId, nodes) ->
            if (nodes.isNotEmpty()) {
                val displayName = nodes.first().serverName
                groups.add(NodeGroup(title = displayName, nodes = nodes.sortedBy { it.nodeName }))
            }
        }
        
    return groups
}
