package com.nerve.android.domain.server

import com.nerve.android.transport.ConnectionState

data class ServerConnection(
    val serverId: String,
    val serverName: String,
    val state: ConnectionState,
)
