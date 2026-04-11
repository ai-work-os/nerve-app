package com.nerve.android.transport.model

import kotlinx.serialization.Serializable

@Serializable
data class PromptResult(
    val stopReason: String? = null,
)
