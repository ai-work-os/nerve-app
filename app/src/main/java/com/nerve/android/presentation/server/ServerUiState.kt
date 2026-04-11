package com.nerve.android.presentation.server

import com.nerve.android.domain.server.ServerConnection
import com.nerve.android.transport.ServerConfig

data class ServerUiState(
    val servers: List<ServerConfig> = emptyList(),
    val connections: List<ServerConnection> = emptyList(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)
