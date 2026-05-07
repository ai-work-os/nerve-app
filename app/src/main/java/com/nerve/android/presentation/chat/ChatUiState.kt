package com.nerve.android.presentation.chat

import com.nerve.android.domain.dm.DmMessage

data class ChatUiState(
    val serverId: String? = null,
    val nodeId: String? = null,
    val nodeName: String? = null,
    val messages: List<DmMessage> = emptyList(),
    val streamingMessage: DmMessage? = null,
    val isStreaming: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val failedMessages: Map<String, String> = emptyMap(), // messageId -> original text for retry
)
