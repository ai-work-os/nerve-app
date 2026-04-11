package com.nerve.android.transport

import java.util.concurrent.ConcurrentHashMap

interface ClientSocket {
    fun send(text: String): Boolean
    fun close(code: Int, reason: String): Boolean
}

interface ClientSocketListener {
    fun onOpen()
    fun onMessage(text: String)
    fun onClosing(code: Int, reason: String)
    fun onClosed(code: Int, reason: String)
    fun onFailure(throwable: Throwable)
}

fun interface SocketEndpoint {
    fun connect(listener: ClientSocketListener): ClientSocket
}

object SocketEndpointRegistry {
    private val endpoints = ConcurrentHashMap<String, SocketEndpoint>()

    fun register(url: String, endpoint: SocketEndpoint) {
        endpoints[url] = endpoint
    }

    fun unregister(url: String) {
        endpoints.remove(url)
    }

    fun connect(url: String, listener: ClientSocketListener): ClientSocket? =
        endpoints[url]?.connect(listener)
}
