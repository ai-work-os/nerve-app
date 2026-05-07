package com.nerve.android.transport

import com.nerve.android.transport.model.ChannelInfo
import com.nerve.android.transport.model.NodeInfo
import com.nerve.android.transport.model.PromptResult
import com.nerve.android.transport.model.SpawnResult
import com.nerve.android.util.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

fun interface BackoffStrategy {
    fun nextDelayMillis(attempt: Int): Long
}

class ExponentialBackoffStrategy : BackoffStrategy {
    override fun nextDelayMillis(attempt: Int): Long {
        val seconds = 1L shl (attempt - 1).coerceAtMost(4)
        return (seconds * 1000).coerceAtMost(30_000)
    }
}

class RealNerveClient(
    private val endpoint: String? = null,
    private val requestTimeoutMs: Long = 120_000,
    private val backoffStrategy: BackoffStrategy = ExponentialBackoffStrategy(),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
) : NerveClient {
    internal val httpClient: OkHttpClient get() = okHttpClient
    internal val configuredRequestTimeoutMs: Long get() = requestTimeoutMs

    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val eventFlow = MutableSharedFlow<NerveEvent>(extraBufferCapacity = 32)
    private val nextRequestId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val bufferedResponses = ConcurrentHashMap<Long, RpcResponse>()
    private val subscribedNodeIds = linkedSetOf<String>()
    private val reconnectMutex = Mutex()
    private val reconnectWake = kotlinx.coroutines.channels.Channel<Unit>(
        capacity = kotlinx.coroutines.channels.Channel.CONFLATED,
    )

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastServer: ServerConfig? = null
    private var lastRegistration: ClientRegistration? = null
    private var socket: ClientSocket? = null
    private var reconnecting = false
    private var manualDisconnect = false

    override val connectionState = state.asStateFlow()
    override val events = eventFlow.asSharedFlow()

    override suspend fun connect(server: ServerConfig, registration: ClientRegistration) {
        lastServer = server
        lastRegistration = registration
        manualDisconnect = false
        Logger.debug("NerveClient", "connect_begin", mapOf("url" to resolveUrl(server)))
        val dispatcher =
            kotlinx.coroutines.currentCoroutineContext()[ContinuationInterceptor] as? CoroutineDispatcher
                ?: Dispatchers.IO
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        transitionTo(ConnectionState.CONNECTING)
        openAndRegister(server, registration)
        transitionTo(ConnectionState.CONNECTED)
        Logger.debug("NerveClient", "connect_success", mapOf("url" to resolveUrl(server)))
    }

    override suspend fun disconnect() {
        Logger.debug("NerveClient", "disconnect_begin")
        manualDisconnect = true
        reconnecting = false
        failPending(RpcException.TransportDisconnected())
        socket?.close(1000, "client disconnect")
        socket = null
        transitionTo(ConnectionState.DISCONNECTED)
        scope.cancel()
        Logger.debug("NerveClient", "disconnect_success")
    }

    override suspend fun call(method: String, params: JsonObject): JsonElement {
        if (connectionState.value != ConnectionState.CONNECTED) {
            throw RpcException.TransportDisconnected()
        }
        return callInternal(method, params)
    }

    override suspend fun listNodes(): List<NodeInfo> {
        val result = call("node.list").jsonObject
        val nodes = result["nodes"] ?: JsonArray(emptyList())
        return RpcSerializer.json.decodeFromJsonElement(nodes)
    }

    override suspend fun listChannels(): List<ChannelInfo> {
        val result = call("channel.list").jsonObject
        val channels = result["channels"] ?: JsonArray(emptyList())
        return RpcSerializer.json.decodeFromJsonElement(channels)
    }

    override suspend fun subscribe(nodeId: String) {
        if (subscribedNodeIds.contains(nodeId)) return
        call("node.subscribe", buildJsonObject { put("nodeId", nodeId) })
        subscribedNodeIds += nodeId
        Logger.debug("NerveClient", "subscribe_success", mapOf("nodeId" to nodeId))
    }

    override suspend fun unsubscribe(nodeId: String) {
        call("node.unsubscribe", buildJsonObject { put("nodeId", nodeId) })
        subscribedNodeIds -= nodeId
        Logger.debug("NerveClient", "unsubscribe_success", mapOf("nodeId" to nodeId))
    }

    override suspend fun prompt(nodeId: String, content: String, attachment: PromptAttachment?): PromptResult {
        val result = call(
            "node.prompt",
            buildJsonObject {
                put("nodeId", nodeId)
                put("content", content)
                if (attachment != null) {
                    put(
                        "attachments",
                        buildJsonArray {
                            when (attachment) {
                                is PromptAttachment.Image -> add(
                                    buildJsonObject {
                                        put("type", "image")
                                        put("mimeType", attachment.mimeType)
                                        put("data", attachment.data)
                                    },
                                )
                            }
                        },
                    )
                }
            },
        ).jsonObject
        return PromptResult(
            stopReason = result["stopReason"]?.jsonPrimitive?.content,
        )
    }

    override suspend fun spawnNode(adapter: String, name: String?, cwd: String?): SpawnResult {
        val result = call(
            "node.spawn",
            buildJsonObject {
                put("adapter", adapter)
                name?.let { put("name", it) }
                cwd?.let { put("cwd", it) }
            },
        ).jsonObject
        return SpawnResult(result["nodeId"]?.jsonPrimitive?.content)
    }

    override suspend fun cancelNode(nodeId: String) {
        call("node.cancel", buildJsonObject { put("nodeId", nodeId) })
    }

    override suspend fun stopNode(nodeId: String) {
        call("node.stop", buildJsonObject { put("nodeId", nodeId) })
    }

    private suspend fun openAndRegister(server: ServerConfig, registration: ClientRegistration) {
        openSocket(resolveUrl(server))
        Logger.debug(
            "NerveClient",
            "register_begin",
            mapOf("name" to registration.name),
        )
        callInternal(
            "node.register",
            buildJsonObject {
                put("name", registration.name)
                put(
                    "capabilities",
                    buildJsonArray {
                        registration.capabilities.forEach { add(JsonPrimitive(it)) }
                    },
                )
                put("permissions", registration.permissions)
            },
        )
        Logger.debug("NerveClient", "register_success", mapOf("name" to registration.name))
    }

    private suspend fun openSocket(url: String) {
        val opened = CompletableDeferred<ClientSocket>()
        val listener = object : ClientSocketListener {
            override fun onOpen() {
                Logger.debug("NerveClient", "socket_open", mapOf("url" to url))
                socket?.let(opened::complete)
            }

            override fun onMessage(text: String) {
                handleIncoming(text)
            }

            override fun onClosing(code: Int, reason: String) {
                Logger.warn("NerveClient", "socket_closing", mapOf("code" to code, "reason" to reason))
                handleSocketLoss()
            }

            override fun onClosed(code: Int, reason: String) {
                Logger.warn("NerveClient", "socket_closed", mapOf("code" to code, "reason" to reason))
                handleSocketLoss()
            }

            override fun onFailure(throwable: Throwable) {
                Logger.error("NerveClient", "socket_failure", mapOf("reason" to throwable.message), throwable)
                if (!opened.isCompleted) opened.completeExceptionally(throwable)
                handleSocketLoss()
            }
        }
        val loopbackSocket = SocketEndpointRegistry.connect(url, listener)
        if (loopbackSocket != null) {
            socket = loopbackSocket
            listener.onOpen()
            opened.await()
            return
        }

        val request = Request.Builder().url(url).build()
        val realSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socket = object : ClientSocket {
                    override fun send(text: String): Boolean = webSocket.send(text)
                    override fun close(code: Int, reason: String): Boolean = webSocket.close(code, reason)
                }
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed(code, reason)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) = listener.onClosing(code, reason)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure(t)
        })
        socket = object : ClientSocket {
            override fun send(text: String): Boolean = realSocket.send(text)
            override fun close(code: Int, reason: String): Boolean = realSocket.close(code, reason)
        }
        withTimeout(okHttpClient.connectTimeoutMillis.toLong()) { opened.await() }
    }

    private fun handleIncoming(text: String) {
        val payload = RpcSerializer.json.parseToJsonElement(text).jsonObject
        val method = payload["method"]?.jsonPrimitive?.content
        val id = payload["id"]?.jsonPrimitive?.content?.toLongOrNull()
        when {
            method != null && id == null -> {
                Logger.debug("NerveClient", "notify_recv", mapOf("method" to method))
                val params = payload["params"]?.jsonObject ?: buildJsonObject {}
                val event = RpcSerializer.parseNotification(method, params)
                if (event == null) {
                    Logger.warn(
                        "NerveClient",
                        "notify_ignored",
                        mapOf("method" to method, "reason" to "unmapped_notification"),
                    )
                } else {
                    eventFlow.tryEmit(event)
                }
            }

            else -> {
                val response = RpcSerializer.decodeResponse(text)
                val responseId = response.id ?: return
                Logger.debug("NerveClient", "rpc_recv", mapOf("requestId" to responseId))
                val deferred = pending.remove(responseId)
                if (deferred == null) {
                    Logger.debug(
                        "NerveClient",
                        "rpc_buffered",
                        mapOf("requestId" to responseId, "reason" to "pending_not_found"),
                    )
                    bufferedResponses[responseId] = response
                    return
                }
                completePending(deferred, response)
            }
        }
    }

    private fun handleSocketLoss() {
        socket = null
        failPending(RpcException.TransportDisconnected())
        if (manualDisconnect) return
        if (state.value == ConnectionState.CONNECTING) return
        if (state.value != ConnectionState.RECONNECTING) {
            transitionTo(ConnectionState.RECONNECTING)
        }
        if (reconnecting) return
        reconnecting = true
        scope.launch {
            reconnectMutex.withLock {
                runReconnectLoop()
            }
        }
    }

    override fun triggerReconnect() {
        reconnectWake.trySend(Unit)
    }

    private suspend fun runReconnectLoop() {
        var attempt = 0
        while (!manualDisconnect) {
            attempt += 1
            val delayMs = backoffStrategy.nextDelayMillis(attempt)
            Logger.debug("NerveClient", "reconnect_attempt", mapOf("attempt" to attempt, "delayMs" to delayMs))
            // Wait either for the backoff or an external wake signal (e.g. network becomes available)
            val woke = kotlinx.coroutines.withTimeoutOrNull(delayMs) {
                reconnectWake.receive()
            }
            if (woke != null) {
                Logger.debug("NerveClient", "reconnect_wake", mapOf("attempt" to attempt))
            }
            val server = lastServer
            val registration = lastRegistration
            if (server == null || registration == null) {
                Logger.warn(
                    "NerveClient",
                    "reconnect_stop",
                    mapOf("reason" to "missing_server_or_registration"),
                )
                break
            }
            try {
                openAndRegister(server, registration)
                replaySubscriptions()
                transitionTo(ConnectionState.CONNECTED)
                Logger.debug("NerveClient", "reconnect_success", mapOf("attempt" to attempt))
                reconnecting = false
                return
            } catch (error: Throwable) {
                Logger.warn("NerveClient", "reconnect_fail", mapOf("attempt" to attempt, "reason" to error.message))
            }
        }
        reconnecting = false
    }

    private suspend fun replaySubscriptions() {
        Logger.debug("NerveClient", "resubscribe_begin", mapOf("count" to subscribedNodeIds.size))
        subscribedNodeIds.forEach { nodeId ->
            Logger.debug("NerveClient", "resubscribe_item", mapOf("nodeId" to nodeId))
            callInternal("node.subscribe", buildJsonObject { put("nodeId", nodeId) })
        }
        Logger.debug("NerveClient", "resubscribe_success", mapOf("count" to subscribedNodeIds.size))
    }

    private suspend fun callInternal(method: String, params: JsonObject): JsonElement {
        return withContext(Dispatchers.IO) {
            val ws = socket ?: throw RpcException.TransportDisconnected()
            val requestId = nextRequestId.getAndIncrement()
            val deferred = CompletableDeferred<JsonElement>()
            pending[requestId] = deferred
            bufferedResponses.remove(requestId)?.let { completePending(deferred, it) }
            val request = RpcRequest(id = requestId, method = method, params = params)
            Logger.debug("NerveClient", "rpc_send", mapOf("method" to method, "requestId" to requestId))
            ws.send(RpcSerializer.encodeRequest(request))
            try {
                withTimeout(requestTimeoutMs) {
                    deferred.await()
                }
            } catch (_: TimeoutCancellationException) {
                pending.remove(requestId)
                Logger.warn(
                    "NerveClient",
                    "rpc_timeout",
                    mapOf("method" to method, "requestId" to requestId, "reason" to "timeout"),
                )
                throw RpcException.RequestTimeout(method, requestId)
            }
        }
    }

    private fun completePending(deferred: CompletableDeferred<JsonElement>, response: RpcResponse) {
        val error = response.error
        if (error != null) {
            Logger.warn("NerveClient", "rpc_error", mapOf("code" to error.code, "reason" to error.message))
            deferred.completeExceptionally(RpcException.ServerError(error.code, error.message))
        } else {
            deferred.complete(response.result ?: buildJsonObject {})
        }
    }

    private fun failPending(error: Throwable) {
        pending.forEach { (_, deferred) -> deferred.completeExceptionally(error) }
        pending.clear()
    }

    private fun resolveUrl(server: ServerConfig): String = endpoint ?: server.webSocketUrl()

    private fun transitionTo(next: ConnectionState) {
        val old = state.value
        if (old == next) return
        Logger.debug("NerveClient", "state_change", mapOf("oldState" to old, "newState" to next))
        state.value = next
    }
}
