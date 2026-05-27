package com.nerve.android.transport

import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import com.nerve.android.transport.model.PromptResult
import com.nerve.android.transport.model.SpawnResult
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

interface NerveClient {
    val connectionState: StateFlow<ConnectionState>
    val events: SharedFlow<NerveEvent>

    suspend fun connect(server: ServerConfig, registration: ClientRegistration)
    suspend fun disconnect()

    suspend fun call(method: String, params: JsonObject = buildJsonObject {}): JsonElement

    suspend fun listNodes(): List<NodeInfo>
    suspend fun listChannels(): List<ChannelInfo>
    suspend fun subscribe(nodeId: String)
    suspend fun unsubscribe(nodeId: String)
    suspend fun prompt(
        nodeId: String,
        content: String,
        attachments: List<PromptAttachment> = emptyList(),
    ): PromptResult
    suspend fun spawnNode(adapter: String, name: String?, cwd: String?): SpawnResult
    suspend fun cancelNode(nodeId: String)
    suspend fun stopNode(nodeId: String)
}

sealed interface PromptAttachment {
    data class Image(
        val mimeType: String,
        val data: String,
    ) : PromptAttachment
}
