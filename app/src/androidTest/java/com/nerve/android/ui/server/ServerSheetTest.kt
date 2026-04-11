package com.nerve.android.ui.server

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
import com.nerve.android.domain.server.ServerConnection
import com.nerve.android.presentation.server.ServerUiState
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.ServerConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun serverSheet_renders_servers_add_remove_and_error() {
        var addCall: Triple<String, String, String>? = null
        var removeCall: String? = null
        composeRule.setContent {
            ServerSheet(
                state = ServerUiState(
                    servers = listOf(
                        ServerConfig("s1", "Home", "10.0.0.1:4800"),
                        ServerConfig("s2", "Lab", "10.0.0.2:4800"),
                    ),
                    connections = listOf(
                        ServerConnection("s1", "Home", ConnectionState.CONNECTED),
                        ServerConnection("s2", "Lab", ConnectionState.RECONNECTING),
                    ),
                    errorMessage = "boom",
                ),
                onAddServer = { id, name, address -> addCall = Triple(id, name, address) },
                onRemoveServer = { removeCall = it },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Lab").assertIsDisplayed()
        composeRule.onNodeWithText("CONNECTED").assertIsDisplayed()
        composeRule.onNodeWithText("RECONNECTING").assertIsDisplayed()
        composeRule.onNodeWithText("boom").assertIsDisplayed()

        composeRule.onNodeWithText("Add server").performClick()
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        composeRule.onNodeWithText("Id").performTextInput("s3")
        composeRule.onNodeWithText("Name").performTextInput("Office")
        composeRule.onNodeWithText("Address").performTextInput("10.0.0.3:4800")
        composeRule.onNodeWithText("Add").performClick()
        check(addCall == Triple("s3", "Office", "10.0.0.3:4800"))

        composeRule.onNodeWithText("Remove Home").performClick()
        check(removeCall == "s1")
    }

    @Test
    fun serverSheet_disables_submit_when_submitting() {
        var showAdd by mutableStateOf(true)
        composeRule.setContent {
            ServerSheet(
                state = ServerUiState(isSubmitting = true),
                onAddServer = { _, _, _ -> },
                onRemoveServer = {},
                onDismiss = {},
                initialShowAddDialog = showAdd,
                onShowAddDialogChange = { showAdd = it },
            )
        }

        composeRule.onNodeWithText("Add").assertIsNotEnabled()
    }
}
