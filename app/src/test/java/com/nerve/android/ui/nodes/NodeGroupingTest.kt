package com.nerve.android.ui.nodes

import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.transport.ServerConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NodeGroupingTest {
    @Test
    fun `groups nodes in servers order, not alphabetical`() {
        val servers = listOf(
            ServerConfig("home", "Home Server", "100.75.43.90:4800"),
            ServerConfig("mac", "Mac", "100.109.126.37:4800"),
        )
        val items = listOf(
            NodeItemUi(serverId = "mac", serverName = "Mac", nodeId = "n2", nodeName = "bot-mac", status = "idle"),
            NodeItemUi(serverId = "home", serverName = "Home Server", nodeId = "n1", nodeName = "bot-home", status = "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("home", "mac"), grouped.map { it.first.id })
    }

    @Test
    fun `nodes within a server are sorted by name`() {
        val servers = listOf(ServerConfig("s1", "S1", "10.0.0.1:4800"))
        val items = listOf(
            NodeItemUi("s1", "S1", "n2", "zeta", "idle"),
            NodeItemUi("s1", "S1", "n1", "alpha", "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("alpha", "zeta"), grouped.single().second.map { it.nodeName })
    }

    @Test
    fun `servers without nodes are omitted`() {
        val servers = listOf(
            ServerConfig("s1", "S1", "10.0.0.1:4800"),
            ServerConfig("s2", "S2", "10.0.0.2:4800"),
        )
        val items = listOf(
            NodeItemUi("s1", "S1", "n1", "bot", "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("s1"), grouped.map { it.first.id })
    }

    @Test
    fun `nodes whose server id is unknown render in a synthetic group at the end`() {
        val servers = listOf(ServerConfig("s1", "S1", "10.0.0.1:4800"))
        val items = listOf(
            NodeItemUi("s1", "S1", "n1", "bot", "idle"),
            NodeItemUi("ghost", "Ghost", "n2", "ghost-bot", "idle"),
        )

        val grouped = groupNodesByServer(items, servers)

        assertEquals(listOf("s1", "ghost"), grouped.map { it.first.id })
        assertEquals("Ghost", grouped.last().first.name)
        assertEquals(listOf("n2"), grouped.last().second.map { it.nodeId })
    }

    @Test
    fun `empty servers list still renders nodes by their own server name`() {
        val items = listOf(
            NodeItemUi("home", "Home Server", "n1", "bot", "idle"),
            NodeItemUi("mac", "Mac", "n2", "bot", "idle"),
        )

        val grouped = groupNodesByServer(items, emptyList())

        assertEquals(setOf("home", "mac"), grouped.map { it.first.id }.toSet())
        assertEquals(2, grouped.size)
    }
}
