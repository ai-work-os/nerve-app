package com.nerve.android.presentation.chat

import com.nerve.android.domain.dm.DmMessage

data class ChatUiState(
    val serverId: String? = null,
    val nodeId: String? = null,
    val nodeName: String? = null,
    val messages: List<DmMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)
