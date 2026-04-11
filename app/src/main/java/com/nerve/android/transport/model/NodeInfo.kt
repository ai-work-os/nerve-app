package com.nerve.android.transport.model

import kotlinx.serialization.Serializable

@Serializable
data class NodeInfo(
    val id: String,
    val name: String,
    val status: String = "idle",
    val adapter: String? = null,
    val cwd: String? = null,
)
