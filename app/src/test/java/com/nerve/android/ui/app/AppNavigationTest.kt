package com.nerve.android.ui.app

import com.nerve.android.domain.server.ServerNode
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.NodeInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AppNavigationTest {
    @Test
    fun `initial state is main with agents tab selected`() {
        val nav = AppNavigation()
        assertIs<AppScreen.Main>(nav.screen)
        assertEquals(0, nav.selectedTab)
    }

    @Test
    fun `selecting tabs updates selectedTab`() {
        val nav = AppNavigation()
        nav.selectTab(1)
        assertEquals(1, nav.selectedTab)
        nav.selectTab(2)
        assertEquals(2, nav.selectedTab)
        nav.selectTab(0)
        assertEquals(0, nav.selectedTab)
    }

    @Test
    fun `selecting life log tab (3) works`() {
        val nav = AppNavigation()
        nav.selectTab(3)
        assertEquals(3, nav.selectedTab)
    }

    @Test
    fun `invalid tab index is ignored`() {
        val nav = AppNavigation()
        nav.selectTab(4)
        assertEquals(0, nav.selectedTab)
        nav.selectTab(-1)
        assertEquals(0, nav.selectedTab)
    }

    @Test
    fun `navigating to chat changes screen`() {
        val nav = AppNavigation()
        nav.openChat("s1", "n1", "bot")
        val screen = nav.screen
        assertIs<AppScreen.Chat>(screen)
        assertEquals("s1", screen.serverId)
        assertEquals("n1", screen.nodeId)
        assertEquals("bot", screen.nodeName)
    }

    @Test
    fun `navigating to channel chat changes screen`() {
        val nav = AppNavigation()
        nav.openChannelChat("s1", "c1", "general")
        val screen = nav.screen
        assertIs<AppScreen.ChannelChat>(screen)
        assertEquals("s1", screen.serverId)
        assertEquals("c1", screen.channelId)
        assertEquals("general", screen.channelName)
    }

    @Test
    fun `back from chat returns to main`() {
        val nav = AppNavigation()
        nav.selectTab(1)
        nav.openChat("s1", "n1", "bot")
        nav.back()
        assertIs<AppScreen.Main>(nav.screen)
        assertEquals(1, nav.selectedTab)
    }

    @Test
    fun `back from channel chat returns to main`() {
        val nav = AppNavigation()
        nav.selectTab(1)
        nav.openChannelChat("s1", "c1", "general")
        nav.back()
        assertIs<AppScreen.Main>(nav.screen)
        assertEquals(1, nav.selectedTab)
    }

    @Test
    fun `back on main is no-op`() {
        val nav = AppNavigation()
        nav.back()
        assertIs<AppScreen.Main>(nav.screen)
    }

    @Test
    fun `guardChat does not navigate back when nodes list is empty`() {
        val nav = AppNavigation()
        nav.openChat("s1", "n1", "bot")
        val servers = listOf(ServerConfig("s1", "Home", "10.0.0.1:4800"))
        val nodes = emptyList<ServerNode>()

        nav.guardChat(servers, nodes)

        assertIs<AppScreen.Chat>(nav.screen)
        assertNull(nav.transientError)
    }

    @Test
    fun `guardChat does not navigate back when only other server nodes are loaded`() {
        val nav = AppNavigation()
        nav.openChat("s1", "n1", "bot")
        val servers = listOf(
            ServerConfig("s1", "Home", "10.0.0.1:4800"),
            ServerConfig("s2", "Test", "10.0.0.1:4801"),
        )
        val nodes = listOf(ServerNode("s2", "Test", NodeInfo("n2", "other-bot")))

        nav.guardChat(servers, nodes)

        assertIs<AppScreen.Chat>(nav.screen)
        assertNull(nav.transientError)
    }

    @Test
    fun `guardChat retargets when same node name has a new id`() {
        val nav = AppNavigation()
        nav.openChat("s1", "old-id", "bot")
        val servers = listOf(ServerConfig("s1", "Home", "10.0.0.1:4800"))
        val nodes = listOf(ServerNode("s1", "Home", NodeInfo("new-id", "bot")))

        nav.guardChat(servers, nodes)

        val screen = assertIs<AppScreen.Chat>(nav.screen)
        assertEquals("new-id", screen.nodeId)
        assertEquals("bot", screen.nodeName)
        assertNull(nav.transientError)
    }

    @Test
    fun `guardChat navigates back silently when nodes loaded but target node missing`() {
        val nav = AppNavigation()
        nav.openChat("s1", "n1", "bot")
        val servers = listOf(ServerConfig("s1", "Home", "10.0.0.1:4800"))
        val nodes = listOf(ServerNode("s1", "Home", NodeInfo("n2", "other-bot")))

        nav.guardChat(servers, nodes)

        assertIs<AppScreen.Main>(nav.screen)
        assertNull(nav.transientError)
    }

    @Test
    fun `guardChat shows error when current server is removed`() {
        val nav = AppNavigation()
        nav.openChat("s1", "n1", "bot")

        nav.guardChat(emptyList(), emptyList())

        assertIs<AppScreen.Main>(nav.screen)
        assertEquals("Current server is unavailable", nav.transientError)
    }

    @Test
    fun `transient error is set and cleared`() {
        val nav = AppNavigation()
        assertNull(nav.transientError)
        nav.showError("something went wrong")
        assertEquals("something went wrong", nav.transientError)
        nav.dismissError()
        assertNull(nav.transientError)
    }
}
