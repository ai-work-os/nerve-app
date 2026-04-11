package com.nerve.android.domain.channel

import kotlinx.serialization.json.JsonObject

data class ChannelMessage(
    val id: String,
    val channelId: String,
    val from: String,
    val content: String,
    val timestamp: Long,
    val metadata: JsonObject? = null,
)
