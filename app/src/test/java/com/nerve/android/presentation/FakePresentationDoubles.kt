package com.nerve.android.presentation

import com.nerve.android.domain.channel.ChannelEventProcessor
import com.nerve.android.domain.server.ServerConnection
import com.nerve.android.domain.server.ServerRegistry
import com.nerve.android.domain.server.ServerNode
import com.nerve.android.domain.server.FakeNerveClient
import com.nerve.android.domain.server.ServerChannel
import com.nerve.android.domain.server.ServerScopedEvent
import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.NerveClient
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

class FakeChannelEventProcessor : ChannelEventProcessor {
    val attachCalls = mutableListOf<String>()
    val cancelledCalls = mutableListOf<String>()

    override suspend fun attach(serverId: String, events: Flow<NerveEvent>) {
        attachCalls += serverId
        try {
            events.collect()
        } finally {
            cancelledCalls += serverId
        }
    }
}

class FakeServerRegistry : ServerRegistry {
    override val servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    override val connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    override val nodes = MutableStateFlow<List<ServerNode>>(emptyList())
    override val channels = MutableStateFlow<List<ServerChannel>>(emptyList())
    override val events = MutableSharedFlow<ServerScopedEvent>(extraBufferCapacity = 64)

    val clients = linkedMapOf<String, FakeNerveClient>()
    val refreshCalls = mutableListOf<String?>()
    val addServerCalls = mutableListOf<ServerConfig>()
    val removeServerCalls = mutableListOf<String>()
    var startCalls = 0

    override suspend fun start(registration: ClientRegistration) {
        startCalls += 1
    }

    override suspend fun refresh(serverId: String?) {
        refreshCalls += serverId
    }

    override suspend fun addServer(config: ServerConfig) {
        addServerCalls += config
    }

    override suspend fun removeServer(serverId: String) {
        removeServerCalls += serverId
    }

    override suspend fun client(serverId: String): NerveClient? = clients[serverId]
}
