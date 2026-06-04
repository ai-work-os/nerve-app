package com.nerve.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    onSend: (String, List<PendingAttachment>) -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit = {},
    onCancel: () -> Unit,
    onStop: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onOpenDm: ((String, String, String) -> Unit)? = null,
    pendingAttachments: List<PendingAttachment> = emptyList(),
    onRemoveAttachment: (Int) -> Unit = {},
    onRetry: (String) -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    var inputBarHeight by remember { mutableStateOf(0.dp) }
    val inputBottomMargin = 32.dp
    val inputToMessageGap = 16.dp
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomWindowPadding = maxOf(navigationBottomPadding, imeBottomPadding)
    val messageListBottomPadding = inputBarHeight + inputBottomMargin + inputToMessageGap + bottomWindowPadding

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                        contentPadding = PaddingValues(bottom = messageListBottomPadding),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = inputBottomMargin)
            ) {
                ChatInputBar(
                    canSend = canSend,
                    isSending = state.isSending,
                    isStreaming = state.isStreaming,
                    modifier = Modifier.onSizeChanged { size ->
                        inputBarHeight = with(density) { size.height.toDp() }
                    },
                    onSend = onSend,
                    onPickImage = onPickImage,
                    onPickFile = onPickFile,
                    pendingAttachments = pendingAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
        }
    }
}
