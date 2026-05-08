package com.nerve.android.domain.server

import android.content.SharedPreferences
import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveClient
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.PromptAttachment
import com.nerve.android.transport.ServerConfig
import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import com.nerve.android.transport.model.PromptResult
import com.nerve.android.transport.model.SpawnResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.CopyOnWriteArrayList

class FakeNerveClient : NerveClient {
    override val connectionState: MutableStateFlow<ConnectionState> =
        MutableStateFlow(ConnectionState.DISCONNECTED)
    override val events: MutableSharedFlow<NerveEvent> = MutableSharedFlow(extraBufferCapacity = 32)

    var nodesResult: Result<List<NodeInfo>> = Result.success(emptyList())
    var channelsResult: Result<List<ChannelInfo>> = Result.success(emptyList())
    var connectResult: Result<Unit> = Result.success(Unit)
    val connectedConfigs = CopyOnWriteArrayList<ServerConfig>()
    val disconnectedCalls = CopyOnWriteArrayList<Unit>()
    val subscribeCalls = CopyOnWriteArrayList<String>()
    val unsubscribeCalls = CopyOnWriteArrayList<String>()
    val promptCalls = CopyOnWriteArrayList<Pair<String, String>>()
    val imagePromptCalls = CopyOnWriteArrayList<String>()
    val spawnCalls = CopyOnWriteArrayList<Triple<String, String?, String?>>()
    val cancelCalls = CopyOnWriteArrayList<String>()
    val stopCalls = CopyOnWriteArrayList<String>()
    val rpcCalls = CopyOnWriteArrayList<Pair<String, JsonObject>>()
    var connectCalls = 0
    var disconnectCalls = 0
    var listNodesCalls = 0
    var listChannelsCalls = 0
    var callResult: Result<JsonElement> = Result.success(buildJsonObject {})
    val callResults: MutableMap<String, Result<JsonElement>> = mutableMapOf()
    var subscribeResult: Result<Unit> = Result.success(Unit)
    var unsubscribeResult: Result<Unit> = Result.success(Unit)
    var promptResult: Result<PromptResult> = Result.success(PromptResult(stopReason = "stop"))
    var spawnResult: Result<SpawnResult> = Result.success(SpawnResult(nodeId = "n1"))
    var stopResult: Result<Unit> = Result.success(Unit)
    var triggerReconnectCalls = 0

    override suspend fun connect(server: ServerConfig, registration: ClientRegistration) {
        connectCalls += 1
        connectResult.getOrThrow()
        connectedConfigs += server
        connectionState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        disconnectCalls += 1
        disconnectedCalls += Unit
        connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun call(method: String, params: JsonObject): JsonElement {
        rpcCalls += method to params
        return (callResults[method] ?: callResult).getOrThrow()
    }

    override suspend fun listNodes(): List<NodeInfo> {
        listNodesCalls += 1
        return nodesResult.getOrThrow()
    }

    override suspend fun listChannels(): List<ChannelInfo> {
        listChannelsCalls += 1
        return channelsResult.getOrThrow()
    }

    override suspend fun subscribe(nodeId: String) {
        subscribeCalls += nodeId
        subscribeResult.getOrThrow()
    }

    override suspend fun unsubscribe(nodeId: String) {
        unsubscribeCalls += nodeId
        unsubscribeResult.getOrThrow()
    }

    override suspend fun prompt(nodeId: String, content: String, attachment: PromptAttachment?): PromptResult {
        promptCalls += nodeId to content
        if (attachment is PromptAttachment.Image) {
            imagePromptCalls += "${attachment.mimeType}:${attachment.data}"
        }
        return promptResult.getOrThrow()
    }

    override suspend fun spawnNode(adapter: String, name: String?, cwd: String?): SpawnResult {
        spawnCalls += Triple(adapter, name, cwd)
        return spawnResult.getOrThrow()
    }

    override suspend fun cancelNode(nodeId: String) {
        cancelCalls += nodeId
    }

    override suspend fun stopNode(nodeId: String) {
        stopCalls += nodeId
        stopResult.getOrThrow()
    }

    override fun triggerReconnect() {
        triggerReconnectCalls += 1
    }
}

class FakeNerveClientFactory(
    private val clientsByServerId: MutableMap<String, ArrayDeque<FakeNerveClient>>,
) : NerveClientFactory {
    val createCalls = CopyOnWriteArrayList<String>()

    override fun create(serverId: String): NerveClient {
        createCalls += serverId
        return clientsByServerId.getValue(serverId).removeFirst()
    }
}

class InMemorySharedPreferences : SharedPreferences {
    private val values = linkedMapOf<String, String?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor(values)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    private class Editor(
        private val values: MutableMap<String, String?>,
    ) : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, String?>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pending[key!!] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pending[key!!] = null
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) values.clear()
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
    }
}

fun serverConfigJson(configs: List<ServerConfig>): String =
    Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ServerConfig.serializer()), configs)
