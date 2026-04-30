package com.nerve.android.ui.server

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nerve.android.domain.server.ServerConnection
import com.nerve.android.presentation.server.ServerUiState
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.ServerConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServersScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun serverCard_refreshesTargetServer() {
        var refreshCall: String? = null
        composeRule.setContent {
            ServersScreen(
                state = ServerUiState(
                    servers = listOf(ServerConfig("s1", "Home", "10.0.0.1:4800")),
                    connections = listOf(ServerConnection("s1", "Home", ConnectionState.RECONNECTING)),
                ),
                isDarkTheme = false,
                onToggleTheme = {},
                onRefresh = {},
                onRefreshServer = { refreshCall = it },
                onAddServer = { _, _, _ -> },
                onRemoveServer = {},
            )
        }

        composeRule.onNodeWithContentDescription("Refresh Home").performClick()

        check(refreshCall == "s1")
    }
}
