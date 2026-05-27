package com.nerve.android.ui.nodes

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nerve.android.presentation.nodes.NodeItemUi
import com.nerve.android.presentation.nodes.NodesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodesScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nodesScreen_renders_list_open_stop_spawn_and_error() {
        var openChat: Triple<String, String, String>? = null
        var stopNode: Pair<String, String>? = null
        var spawnNode: Array<out String?>? = null
        composeRule.setContent {
            NodesScreen(
                state = NodesUiState(
                    items = listOf(
                        NodeItemUi("s1", "Home", "n1", "bot-a", "idle"),
                        NodeItemUi("s2", "Lab", "n2", "bot-b", "busy"),
                    ),
                    errorMessage = "oops",
                ),
                onRefresh = {},
                onOpenChat = { serverId, nodeId, nodeName -> openChat = Triple(serverId, nodeId, nodeName) },
                onOpenServers = {},
                onStop = { serverId, nodeId -> stopNode = serverId to nodeId },
                onSpawn = { serverId, adapter, name, cwd -> spawnNode = arrayOf(serverId, adapter, name, cwd) },
            )
        }

        composeRule.onNodeWithText("bot-a").assertIsDisplayed()
        composeRule.onNodeWithText("Home · idle").assertIsDisplayed()
        composeRule.onNodeWithText("bot-b").assertIsDisplayed()
        composeRule.onNodeWithText("Lab · busy").assertIsDisplayed()
        composeRule.onNodeWithText("oops").assertIsDisplayed()

        composeRule.onNodeWithText("Open bot-a").performClick()
        check(openChat == Triple("s1", "n1", "bot-a"))

        composeRule.onNodeWithText("Stop bot-b").performClick()
        check(stopNode == ("s2" to "n2"))

        composeRule.onNodeWithText("Spawn").performClick()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()
        composeRule.onNodeWithText("Server Id").performTextInput("s1")
        composeRule.onNodeWithText("Adapter").performTextInput("claude")
        composeRule.onNodeWithText("Name").performTextInput("new-bot")
        composeRule.onNodeWithText("Cwd").performTextInput("/tmp")
        composeRule.onNodeWithText("Create").performClick()
        check(spawnNode?.toList() == listOf("s1", "claude", "new-bot", "/tmp"))
    }

    @Test
    fun nodesScreen_shows_empty_and_disables_refresh_when_refreshing() {
        var showSpawn by mutableStateOf(false)
        composeRule.setContent {
            NodesScreen(
                state = NodesUiState(isRefreshing = true),
                onRefresh = {},
                onOpenChat = { _, _, _ -> },
                onOpenServers = {},
                onStop = { _, _ -> },
                onSpawn = { _, _, _, _ -> },
                initialShowSpawnDialog = showSpawn,
                onShowSpawnDialogChange = { showSpawn = it },
            )
        }

        composeRule.onNodeWithText("No nodes yet").assertIsDisplayed()
        composeRule.onNodeWithText("Refresh").assertIsNotEnabled()
    }
}
