package com.nerve.android.transport.model

import kotlinx.serialization.Serializable

@Serializable
data class ChannelInfo(
    val id: String,
    val name: String,
)
