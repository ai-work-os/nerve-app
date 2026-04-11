package com.nerve.android.transport

import kotlinx.serialization.Serializable

@Serializable
data class ClientRegistration(
    val name: String,
    val capabilities: List<String> = listOf("ui"),
    val permissions: String = "operator",
)
