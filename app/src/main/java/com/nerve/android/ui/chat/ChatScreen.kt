package com.nerve.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerve.android.domain.dm.ContentBlock
import com.nerve.android.presentation.chat.ChatUiState
import com.nerve.android.util.Logger

@Composable
fun ChatScreen(
    state: ChatUiState,
    streamingText: String,
    streamingBlocks: List<ContentBlock> = emptyList(),
    canSend: Boolean = true,
    onSend: (String) -> Unit,
    onPickImage: () -> Unit,
    onCancel: () -> Unit,
    onStop: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        Logger.d("ChatScreen", "chat ui error visible=${state.errorMessage != null}")
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopBar(
                state = state,
                onCancel = onCancel,
                onStop = onStop,
                onBack = onBack,
            )
            state.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            HorizontalDivider()
            Column(modifier = Modifier.weight(1f)) {
                MessageList(
                    messages = state.messages,
                    isStreaming = state.isStreaming,
                    streamingText = streamingText,
                    streamingBlocks = streamingBlocks,
                )
            }
            HorizontalDivider()
            ChatInputBar(
                canSend = canSend,
                isSending = state.isSending,
                onSend = onSend,
                onPickImage = onPickImage,
            )
        }
    }
}
