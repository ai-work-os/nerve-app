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
    private val requestTimeoutMs: Long = 10_000,
    private val backoffStrategy: BackoffStrategy = ExponentialBackoffStrategy(),
    private val okHttpClient: OkHttpClient = OkHttpClient(),
) : NerveClient {
    private val state = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val eventFlow = MutableSharedFlow<NerveEvent>(extraBufferCapacity = 32)
    private val nextRequestId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonElement>>()
    private val bufferedResponses = ConcurrentHashMap<Long, RpcResponse>()
    private val subscribedNodeIds = linkedSetOf<String>()
    private val reconnectMutex = Mutex()

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
        val dispatcher =
            kotlinx.coroutines.currentCoroutineContext()[ContinuationInterceptor] as? CoroutineDispatcher
                ?: Dispatchers.IO
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        transitionTo(ConnectionState.CONNECTING)
        openAndRegister(server, registration)
        transitionTo(ConnectionState.CONNECTED)
    }

    override suspend fun disconnect() {
        manualDisconnect = true
        reconnecting = false
        failPending(RpcException.TransportDisconnected())
        socket?.close(1000, "client disconnect")
        socket = null
        transitionTo(ConnectionState.DISCONNECTED)
        scope.cancel()
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
        Logger.d("NerveClient", "subscribe nodeId=$nodeId")
    }

    override suspend fun unsubscribe(nodeId: String) {
        call("node.unsubscribe", buildJsonObject { put("nodeId", nodeId) })
        subscribedNodeIds -= nodeId
        Logger.d("NerveClient", "unsubscribe nodeId=$nodeId")
    }

    override suspend fun prompt(nodeId: String, content: String): PromptResult {
        val result = call(
            "node.prompt",
            buildJsonObject {
                put("nodeId", nodeId)
                put("content", content)
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
        Logger.d(
            "NerveClient",
            "register name=${registration.name} capabilities=${registration.capabilities} permissions=${registration.permissions}",
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
    }

    private suspend fun openSocket(url: String) {
        Logger.d("NerveClient", "connect server=$url")
        val opened = CompletableDeferred<ClientSocket>()
        val listener = object : ClientSocketListener {
            override fun onOpen() {
                socket?.let(opened::complete)
            }

            override fun onMessage(text: String) {
                handleIncoming(text)
            }

            override fun onClosing(code: Int, reason: String) {
                Logger.w("NerveClient", "socket failure reason=$reason")
                handleSocketLoss()
            }

            override fun onClosed(code: Int, reason: String) {
                Logger.w("NerveClient", "socket failure reason=$reason")
                handleSocketLoss()
            }

            override fun onFailure(throwable: Throwable) {
                Logger.e("NerveClient", "socket failure reason=${throwable.message}", throwable)
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
        opened.await()
    }

    private fun handleIncoming(text: String) {
        val payload = RpcSerializer.json.parseToJsonElement(text).jsonObject
        val method = payload["method"]?.jsonPrimitive?.content
        val id = payload["id"]?.jsonPrimitive?.content?.toLongOrNull()
        when {
            method != null && id == null -> {
                Logger.d("NerveClient", "notify method=$method")
                val params = payload["params"]?.jsonObject ?: buildJsonObject {}
                RpcSerializer.parseNotification(method, params)?.let { eventFlow.tryEmit(it) }
            }

            else -> {
                val response = RpcSerializer.decodeResponse(text)
                val responseId = response.id ?: return
                Logger.d("NerveClient", "rpc recv id=$responseId")
                val deferred = pending.remove(responseId)
                if (deferred == null) {
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

    private suspend fun runReconnectLoop() {
        var attempt = 0
        while (!manualDisconnect) {
            attempt += 1
            val delayMs = backoffStrategy.nextDelayMillis(attempt)
            Logger.d("NerveClient", "reconnect attempt=$attempt delayMs=$delayMs")
            delay(delayMs)
            val server = lastServer ?: break
            val registration = lastRegistration ?: break
            try {
                openAndRegister(server, registration)
                replaySubscriptions()
                transitionTo(ConnectionState.CONNECTED)
                reconnecting = false
                return
            } catch (_: Throwable) {
            }
        }
        reconnecting = false
    }

    private suspend fun replaySubscriptions() {
        Logger.d("NerveClient", "resubscribe count=${subscribedNodeIds.size}")
        subscribedNodeIds.forEach { nodeId ->
            Logger.d("NerveClient", "resubscribe nodeId=$nodeId")
            callInternal("node.subscribe", buildJsonObject { put("nodeId", nodeId) })
        }
    }

    private suspend fun callInternal(method: String, params: JsonObject): JsonElement {
        return withContext(Dispatchers.IO) {
            val ws = socket ?: throw RpcException.TransportDisconnected()
            val requestId = nextRequestId.getAndIncrement()
            val deferred = CompletableDeferred<JsonElement>()
            pending[requestId] = deferred
            bufferedResponses.remove(requestId)?.let { completePending(deferred, it) }
            val request = RpcRequest(id = requestId, method = method, params = params)
            Logger.d("NerveClient", "rpc send method=$method id=$requestId")
            ws.send(RpcSerializer.encodeRequest(request))
            try {
                withTimeout(requestTimeoutMs) {
                    deferred.await()
                }
            } catch (_: TimeoutCancellationException) {
                pending.remove(requestId)
                Logger.w("NerveClient", "rpc timeout id=$requestId method=$method")
                throw RpcException.RequestTimeout(method, requestId)
            }
        }
    }

    private fun completePending(deferred: CompletableDeferred<JsonElement>, response: RpcResponse) {
        val error = response.error
        if (error != null) {
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
        Logger.d("NerveClient", "state old=$old new=$next")
        state.value = next
    }
}
