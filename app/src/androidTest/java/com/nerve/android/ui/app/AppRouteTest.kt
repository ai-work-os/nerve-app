package com.nerve.android.ui.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nerve.android.NerveApp
import com.nerve.android.domain.server.NerveClientFactory
import com.nerve.android.domain.server.RealServerRegistry
import com.nerve.android.domain.server.ServerConfigStore
import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveClient
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import com.nerve.android.transport.model.PromptResult
import com.nerve.android.transport.model.SpawnResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun appRoute_real_assembly_opens_servers_opens_chat_and_backs() {
        val (app, _) = prepareApp()

        composeRule.setContent {
            AppRoute(app)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Nerve").assertIsDisplayed()
        composeRule.onNodeWithText("Open bot-a").assertIsDisplayed()
        composeRule.onNodeWithText("Servers").performClick()
        composeRule.onNodeWithText("Server sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("CONNECTED").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onAllNodesWithText("Server sheet").assertCountEquals(0)

        composeRule.onNodeWithText("Open bot-a").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("bot-a").assertIsDisplayed()
        composeRule.onAllNodesWithText("Back").assertCountEquals(1)
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Open bot-a").assertIsDisplayed()
    }

    @Test
    fun appRoute_real_assembly_backs_to_nodes_when_current_chat_target_disappears() {
        val (app, registry) = prepareApp()

        composeRule.setContent {
            AppRoute(app)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open bot-a").performClick()
        composeRule.onNodeWithText("bot-a").assertIsDisplayed()

        runBlocking {
            registry.removeServer("s1")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Current server is unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("No nodes yet").assertIsDisplayed()
    }

    @Test
    fun appRoute_real_assembly_transient_error_can_close() {
        val (app, registry) = prepareApp()

        composeRule.setContent {
            AppRoute(app)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open bot-a").performClick()
        runBlocking {
            registry.removeServer("s1")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Current server is unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.onAllNodesWithText("Current server is unavailable").assertCountEquals(0)
    }

    private fun prepareApp(): Pair<NerveApp, RealServerRegistry> {
        val app = composeRule.activity.application as NerveApp
        val registry = RealServerRegistry(
            configStore = StaticServerConfigStore(
                listOf(ServerConfig("s1", "Home", "10.0.0.1:4800")),
            ),
            clientFactory = NerveClientFactory { TestNerveClient() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
        setField(app, "serverRegistry", registry)
        runBlocking {
            registry.start(ClientRegistration(name = "android-test"))
        }
        return app to registry
    }

    private fun setField(target: Any, name: String, value: Any) {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }
}

private class StaticServerConfigStore(
    private var configs: List<ServerConfig>,
) : ServerConfigStore {
    override suspend fun load(): List<ServerConfig> = configs

    override suspend fun saveAll(configs: List<ServerConfig>) {
        this.configs = configs
    }

    override suspend fun upsert(config: ServerConfig) {
        configs = configs.filterNot { it.id == config.id } + config
    }

    override suspend fun remove(serverId: String) {
        configs = configs.filterNot { it.id == serverId }
    }
}

private class TestNerveClient : NerveClient {
    override val connectionState: StateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.CONNECTED)
    override val events: SharedFlow<NerveEvent> = MutableSharedFlow(extraBufferCapacity = 32)

    override suspend fun connect(server: ServerConfig, registration: ClientRegistration) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun call(method: String, params: JsonObject): JsonElement = buildJsonObject {}

    override suspend fun listNodes(): List<NodeInfo> = listOf(NodeInfo("n1", "bot-a"))

    override suspend fun listChannels(): List<ChannelInfo> = emptyList()

    override suspend fun subscribe(nodeId: String) = Unit

    override suspend fun unsubscribe(nodeId: String) = Unit

    override suspend fun prompt(
        nodeId: String,
        content: String,
        attachments: List<com.nerve.android.transport.PromptAttachment>,
    ): PromptResult =
        PromptResult(stopReason = "stop")

    override suspend fun spawnNode(adapter: String, name: String?, cwd: String?): SpawnResult =
        SpawnResult(nodeId = "n2")

    override suspend fun cancelNode(nodeId: String) = Unit
    override suspend fun stopNode(nodeId: String) = Unit
}
