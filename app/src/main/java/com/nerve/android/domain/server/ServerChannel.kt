package com.nerve.android.domain.server

import com.nerve.android.transport.model.ChannelInfo

data class ServerChannel(
    val serverId: String,
    val serverName: String,
    val channel: ChannelInfo,
)
