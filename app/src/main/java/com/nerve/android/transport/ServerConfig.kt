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
}
