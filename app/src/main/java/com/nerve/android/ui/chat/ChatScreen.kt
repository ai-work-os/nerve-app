package com.nerve.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onOpenDm: ((String, String, String) -> Unit)? = null,
    pendingAttachment: PendingAttachment? = null,
    onClearAttachment: () -> Unit = {},
    onRetry: (String) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopBar(
                state = state,
                onCancel = onCancel,
                onStop = onStop,
                onBack = onBack,
            )
            
            Box(modifier = Modifier.weight(1f)) {
                MessageList(
                    messages = state.messages,
                    isStreaming = state.isStreaming,
                    streamingText = streamingText,
                    streamingBlocks = streamingBlocks,
                    onOpenDm = onOpenDm,
                    failedMessageIds = state.failedMessages.keys,
                    onRetry = onRetry,
                )
            }
            
            Spacer(modifier = Modifier.height(100.dp)) // Space for floating input
        }
        
        // Floating Input handled by ChatRoute / AppRoute parent or internal Box
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 32.dp)) {
                ChatInputBar(
                    canSend = canSend,
                    isSending = state.isSending,
                    onSend = onSend,
                    onPickImage = onPickImage,
                    pendingAttachment = pendingAttachment,
                    onClearAttachment = onClearAttachment,
                )
            }
        }
    }
}
