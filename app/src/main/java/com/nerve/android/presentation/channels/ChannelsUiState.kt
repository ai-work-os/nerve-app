package com.nerve.android.presentation.channels

import com.nerve.android.domain.channel.ChannelMessage
import com.nerve.android.domain.channel.ChannelMeta
import com.nerve.android.domain.server.ServerChannel

data class ChannelsUiState(
    val channels: List<ServerChannel> = emptyList(),
    val currentServerId: String? = null,
    val currentChannelId: String? = null,
    val currentChannelName: String? = null,
    val currentMeta: ChannelMeta? = null,
    val messages: List<ChannelMessage> = emptyList(),
    val isJoining: Boolean = false,
    val isPosting: Boolean = false,
    val errorMessage: String? = null,
)
