package com.nerve.android.domain.channel

data class ChannelMeta(
    val channelId: String,
    val name: String?,
    val cwd: String? = null,
    val isClosed: Boolean = false,
)
