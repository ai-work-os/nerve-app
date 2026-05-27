package com.nerve.android.domain.server

import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveClient
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import com.nerve.android.transport.model.PromptResult
import com.nerve.android.transport.model.SpawnResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerRegistryParallelConnectTest {
    @Test
    fun `slow server does not block fast server connection`() = runTest {
        val slowGate = CompletableDeferred<Unit>()
        val fastConnectOrder = CompletableDeferred<Unit>()

        val slowClient = BlockingNerveClient(slowGate)
        val fastClient = FakeNerveClient().apply {
            nodesResult = Result.success(listOf(NodeInfo("n1", "bot")))
            channelsResult = Result.success(listOf(ChannelInfo("c1", "general")))
        }

        val store = FakeServerConfigStore(
            listOf(
                ServerConfig("slow", "Slow", "10.0.0.1:4800"),
                ServerConfig("fast", "Fast", "10.0.0.2:4800"),
            ),
        )
        val factory = OrderTrackingFactory(
            mapOf("slow" to slowClient, "fast" to fastClient),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registry = RealServerRegistry(store, factory, scope)

        // Release slow gate after fast connects so start() can finish
        launch {
            // If parallel, fast connects quickly while slow blocks
            // Wait until fast is connected then release slow
            while (fastClient.connectCalls == 0) {
                kotlinx.coroutines.delay(10)
            }
            slowGate.complete(Unit)
        }

        withTimeout(5000) {
            registry.start(ClientRegistration(name = "android-ui"))
        }

        // Both created
        assertEquals(2, factory.createCalls.size)
        // Fast connected despite slow blocking
        assertEquals(ConnectionState.CONNECTED, fastClient.connectionState.value)
    }
}

/** A NerveClient whose connect() suspends until the given gate completes. */
private class BlockingNerveClient(
    private val gate: CompletableDeferred<Unit>,
) : NerveClient {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val events = MutableSharedFlow<NerveEvent>(extraBufferCapacity = 32)

    override suspend fun connect(server: ServerConfig, registration: ClientRegistration) {
        gate.await()
        connectionState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() { connectionState.value = ConnectionState.DISCONNECTED }
    override suspend fun call(method: String, params: JsonObject): JsonElement = buildJsonObject {}
    override suspend fun listNodes(): List<NodeInfo> = emptyList()
    override suspend fun listChannels(): List<ChannelInfo> = emptyList()
    override suspend fun subscribe(nodeId: String) {}
    override suspend fun unsubscribe(nodeId: String) {}
    override suspend fun prompt(
        nodeId: String,
        content: String,
        attachments: List<com.nerve.android.transport.PromptAttachment>,
    ) = PromptResult(stopReason = null)
    override suspend fun spawnNode(adapter: String, name: String?, cwd: String?) = SpawnResult(nodeId = null)
    override suspend fun cancelNode(nodeId: String) {}
    override suspend fun stopNode(nodeId: String) {}
}

private class OrderTrackingFactory(
    private val clients: Map<String, NerveClient>,
) : NerveClientFactory {
    val createCalls = mutableListOf<String>()
    override fun create(serverId: String): NerveClient {
        createCalls += serverId
        return clients.getValue(serverId)
    }
}
