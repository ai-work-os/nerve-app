package com.nerve.android.transport

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val address: String,
) {
    fun webSocketUrl(): String = if (address.startsWith("ws://") || address.startsWith("wss://")) {
        address
    } else {
        "ws://$address"
    }

    fun httpUrl(): String = when {
        address.startsWith("wss://") -> "https://${address.removePrefix("wss://")}"
        address.startsWith("ws://") -> "http://${address.removePrefix("ws://")}"
        address.startsWith("https://") || address.startsWith("http://") -> address
        else -> "http://$address"
    }
}
