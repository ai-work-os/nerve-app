package com.nerve.android.transport

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class RecordedClientMessage(
    val id: Long?,
    val method: String,
    val params: Map<String, Any?>,
)

class FakeNerveWsServer {
    private val json = Json { ignoreUnknownKeys = true }
    private val scripts = mutableMapOf<String, ArrayDeque<(Long?) -> Unit>>()
    private val inbound = LinkedBlockingQueue<RecordedClientMessage>()
    private val methods = mutableListOf<String>()
    private var listener: ClientSocketListener? = null
    private val key = "fake-${counter.incrementAndGet()}:4800"

    val url: String = "ws://$key"

    init {
        SocketEndpointRegistry.register(url) { socketListener ->
            listener = socketListener
            object : ClientSocket {
                override fun send(text: String): Boolean {
                    handleClientMessage(text)
                    return true
                }

                override fun close(code: Int, reason: String): Boolean {
                    listener?.onClosed(code, reason)
                    return true
                }
            }
        }
    }

    fun address(): String = key

    fun enqueueRegisterSuccess() {
        enqueueScript("node.register") { id ->
            sendResponse(id, """{"nodeId":"android-ui"}""")
        }
    }

    fun enqueueSubscribeSuccess(nodeId: String) {
        enqueueScript("node.subscribe") { id ->
            sendResponse(id, """{"nodeId":"$nodeId","ok":true}""")
        }
    }

    fun enqueueRpcResult(method: String, resultProvider: () -> String) {
        enqueueScript(method) { id -> sendResponse(id, resultProvider()) }
    }

    fun enqueueRpcError(method: String, code: Int, message: String) {
        enqueueScript(method) { id ->
            listener?.onMessage("""{"jsonrpc":"2.0","id":$id,"error":{"code":$code,"message":"$message"}}""")
        }
    }

    fun takeClientMessageAsJson(timeoutMs: Long = 1000): RecordedClientMessage =
        inbound.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: error("no client message in ${timeoutMs}ms")

    fun receivedMethods(): List<String> = methods.toList()

    fun closeConnection() {
        listener?.onClosed(1001, "server close")
    }

    fun sendNotification(method: String, params: String) {
        listener?.onMessage("""{"jsonrpc":"2.0","method":"$method","params":$params}""")
    }

    fun replyOutOfOrder(
        firstMethod: String,
        firstResult: String,
        secondMethod: String,
        secondResult: String,
    ) {
        val seen = mutableMapOf<String, RecordedClientMessage>()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250)
        while ((seen[firstMethod] == null || seen[secondMethod] == null) && System.nanoTime() < deadline) {
            inbound.poll(25, TimeUnit.MILLISECONDS)?.let { message ->
                seen[message.method] = message
            }
        }

        val second = seen[secondMethod]
        val first = seen[firstMethod]
        val firstId = first?.id ?: second?.id?.plus(1) ?: 3L
        val secondId = second?.id ?: first?.id?.minus(1) ?: 2L
        sendResponse(firstId, firstResult)
        sendResponse(secondId, secondResult)
    }

    private fun enqueueScript(method: String, block: (Long?) -> Unit) {
        scripts.getOrPut(method) { ArrayDeque() }.addLast(block)
    }

    private fun handleClientMessage(text: String) {
        val raw = json.parseToJsonElement(text).jsonObject
        val method = raw["method"]?.jsonPrimitive?.content ?: return
        val id = raw["id"]?.jsonPrimitive?.longOrNull
        methods += method
        inbound.put(RecordedClientMessage(id, method, toMap(raw["params"]?.jsonObject ?: buildJsonObject {})))
        scripts[method]?.removeFirstOrNull()?.invoke(id)
    }

    private fun sendResponse(id: Long?, resultJson: String) {
        listener?.onMessage("""{"jsonrpc":"2.0","id":$id,"result":$resultJson}""")
    }

    private fun toMap(jsonObject: JsonObject): Map<String, Any?> = jsonObject.mapValues { (_, value) ->
        when (value) {
            is JsonObject -> toMap(value)
            is JsonArray -> value.map { item ->
                when (item) {
                    is JsonPrimitive -> if (item.isString) item.content else item.content
                    is JsonObject -> toMap(item)
                    else -> item.toString()
                }
            }
            is JsonPrimitive -> {
                when {
                    value.isString -> value.content
                    value.longOrNull != null -> value.long
                    value.booleanOrNull != null -> value.boolean
                    else -> value.content
                }
            }

            else -> value.toString()
        }
    }

    companion object {
        private val counter = AtomicInteger()
    }
}
