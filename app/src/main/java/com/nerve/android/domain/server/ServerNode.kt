package com.nerve.android.domain.server

import com.nerve.android.transport.model.NodeInfo

data class ServerNode(
    val serverId: String,
    val serverName: String,
    val node: NodeInfo,
)
