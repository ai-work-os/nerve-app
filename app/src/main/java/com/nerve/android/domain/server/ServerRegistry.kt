package com.nerve.android.domain.server

import com.nerve.android.transport.ClientRegistration
import com.nerve.android.transport.ConnectionState
import com.nerve.android.transport.NerveClient
import com.nerve.android.transport.NerveEvent
import com.nerve.android.transport.ServerConfig
import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface ServerRegistry {
    val servers: StateFlow<List<ServerConfig>>
    val connections: StateFlow<List<ServerConnection>>
    val nodes: StateFlow<List<ServerNode>>
    val channels: StateFlow<List<ServerChannel>>
    val events: SharedFlow<ServerScopedEvent>

    suspend fun start(registration: ClientRegistration)
    suspend fun refresh(serverId: String? = null)
    suspend fun addServer(config: ServerConfig)
    suspend fun removeServer(serverId: String)
    suspend fun client(serverId: String): NerveClient?
}

class RealServerRegistry(
    private val configStore: ServerConfigStore,
    private val clientFactory: NerveClientFactory,
    private val scope: CoroutineScope,
) : ServerRegistry {
    private val _servers = MutableStateFlow<List<ServerConfig>>(emptyList())
    private val _connections = MutableStateFlow<List<ServerConnection>>(emptyList())
    private val _nodes = MutableStateFlow<List<ServerNode>>(emptyList())
    private val _channels = MutableStateFlow<List<ServerChannel>>(emptyList())
    private val _events = MutableSharedFlow<ServerScopedEvent>(replay = 1, extraBufferCapacity = 64)

    private val clients = ConcurrentHashMap<String, NerveClient>()
    private val connectionJobs = ConcurrentHashMap<String, Job>()
    private val eventJobs = ConcurrentHashMap<String, Job>()
    private val nodeCache = ConcurrentHashMap<String, List<com.nerve.android.transport.model.NodeInfo>>()
    private val channelCache = ConcurrentHashMap<String, List<com.nerve.android.transport.model.ChannelInfo>>()
    private val connectionCache = ConcurrentHashMap<String, ConnectionState>()

    private var started = false
    private var registration: ClientRegistration? = null

    override val servers: StateFlow<List<ServerConfig>> = _servers.asStateFlow()
    override val connections: StateFlow<List<ServerConnection>> = _connections.asStateFlow()
    override val nodes: StateFlow<List<ServerNode>> = _nodes.asStateFlow()
    override val channels: StateFlow<List<ServerChannel>> = _channels.asStateFlow()
    override val events: SharedFlow<ServerScopedEvent> = _events.asSharedFlow()

    override suspend fun start(registration: ClientRegistration) {
        if (started) return
        started = true
        this.registration = registration
        val configs = configStore.load()
        Logger.debug("ServerRegistry", "registry_start", mapOf("serverCount" to configs.size))
        _servers.value = configs
        val jobs = configs.map { config ->
            scope.launch {
                runCatching { connectServer(config, registration) }
                    .onFailure { handleConnectFailure(config.id, it) }
            }
        }
        jobs.forEach { it.join() }
        refresh()
        Logger.debug("ServerRegistry", "registry_started", mapOf("serverCount" to configs.size))
    }

    override suspend fun refresh(serverId: String?) {
        val targetConfigs = if (serverId == null) {
            _servers.value
        } else {
            _servers.value.filter { it.id == serverId }
        }
        Logger.debug(
            "ServerRegistry",
            "refresh_begin",
            mapOf("serverId" to serverId, "targetCount" to targetConfigs.size),
        )
        for (config in targetConfigs) {
            val client = clients[config.id] ?: continue
            val refreshedNodes = runCatching { client.listNodes() }
            val refreshedChannels = runCatching { client.listChannels() }
            refreshedNodes.onSuccess { nodeCache[config.id] = it }
                .onFailure {
                    nodeCache.remove(config.id)
                    Logger.warn(
                        "ServerRegistry",
                        "refresh_fail",
                        mapOf("serverId" to config.id, "target" to "nodes", "reason" to it.message),
                    )
                }
            refreshedChannels.onSuccess { channelCache[config.id] = it }
                .onFailure {
                    channelCache.remove(config.id)
                    Logger.warn(
                        "ServerRegistry",
                        "refresh_fail",
                        mapOf("serverId" to config.id, "target" to "channels", "reason" to it.message),
                    )
                }
            Logger.debug(
                "ServerRegistry",
                "refresh_success",
                mapOf(
                    "serverId" to config.id,
                    "nodeCount" to nodeCache[config.id]?.size,
                    "channelCount" to channelCache[config.id]?.size,
                ),
            )
        }
        publishSnapshots()
    }

    override suspend fun addServer(config: ServerConfig) {
        Logger.debug("ServerRegistry", "server_add", mapOf("serverId" to config.id, "address" to config.address))
        configStore.upsert(config)
        _servers.value = _servers.value.filterNot { it.id == config.id } + config
        disconnectServer(config.id)
        registration?.let {
            runCatching { connectServer(config, it) }
                .onFailure { error -> handleConnectFailure(config.id, error) }
        }
        refresh(config.id)
    }

    override suspend fun removeServer(serverId: String) {
        Logger.debug("ServerRegistry", "server_remove", mapOf("serverId" to serverId))
        disconnectServer(serverId)
        nodeCache.remove(serverId)
        channelCache.remove(serverId)
        connectionCache.remove(serverId)
        _servers.value = _servers.value.filterNot { it.id == serverId }
        configStore.remove(serverId)
        publishSnapshots()
    }

    override suspend fun client(serverId: String): NerveClient? = clients[serverId]

    private suspend fun connectServer(config: ServerConfig, registration: ClientRegistration) {
        Logger.debug("ServerRegistry", "client_create", mapOf("serverId" to config.id))
        val client = clientFactory.create(config.id)
        clients[config.id] = client
        connectionJobs[config.id] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.connectionState.collect { state ->
                connectionCache[config.id] = state
                Logger.debug("ServerRegistry", "state_change", mapOf("serverId" to config.id, "state" to state))
                publishConnections()
            }
        }
        eventJobs[config.id] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                client.events.collect { event ->
                    Logger.debug(
                        "ServerRegistry",
                        "event_forward",
                        mapOf("serverId" to config.id, "eventType" to event::class.simpleName),
                    )
                    _events.emit(ServerScopedEvent(config.id, event))
                }
            }
        }
        Logger.debug("ServerRegistry", "connect_begin", mapOf("serverId" to config.id))
        client.connect(config, registration)
    }

    private suspend fun disconnectServer(serverId: String) {
        Logger.debug("ServerRegistry", "disconnect_begin", mapOf("serverId" to serverId))
        connectionJobs.remove(serverId)?.cancel()
        eventJobs.remove(serverId)?.cancel()
        clients.remove(serverId)?.disconnect()
        Logger.debug("ServerRegistry", "disconnect_success", mapOf("serverId" to serverId))
    }

    private fun handleConnectFailure(serverId: String, error: Throwable) {
        Logger.warn("ServerRegistry", "connect_fail", mapOf("serverId" to serverId, "reason" to error.message))
        connectionJobs.remove(serverId)?.cancel()
        eventJobs.remove(serverId)?.cancel()
        clients.remove(serverId)
        nodeCache.remove(serverId)
        channelCache.remove(serverId)
        connectionCache.remove(serverId)
        publishSnapshots()
    }

    private fun publishSnapshots() {
        publishConnections()
        _nodes.value = _servers.value.flatMap { config ->
            nodeCache[config.id].orEmpty().map { ServerNode(config.id, config.name, it) }
        }
        _channels.value = _servers.value.flatMap { config ->
            channelCache[config.id].orEmpty().map { ServerChannel(config.id, config.name, it) }
        }
    }

    private fun publishConnections() {
        _connections.value = _servers.value.mapNotNull { config ->
            connectionCache[config.id]?.let { ServerConnection(config.id, config.name, it) }
        }
    }
}
