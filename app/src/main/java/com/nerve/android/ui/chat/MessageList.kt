package com.nerve.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.util.Logger

@Composable
fun MessageList(
    messages: List<DmMessage>,
    isStreaming: Boolean,
    streamingText: String,
    onAutoScroll: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isStreaming, streamingText) {
        val extra = if (isStreaming) 1 else 0
        val target = (messages.size + extra - 1).coerceAtLeast(0)
        Logger.d("ChatScreen", "chat ui scroll bottom count=${messages.size + extra}")
        onAutoScroll?.invoke()
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (messages.isEmpty()) {
            item {
                Text(
                    "Start the conversation",
                    modifier = Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(messages, key = { it.id }) { message ->
            MessageBubble(message)
        }
        if (isStreaming) {
            if (streamingText.isNotEmpty()) {
                item("streaming") {
                    MessageBubble(
                        message = DmMessage(
                            id = "streaming",
                            role = DmRole.ASSISTANT,
                            content = streamingText,
                            timestamp = 0L,
                            nodeId = "",
                            nodeName = "",
                        ),
                        testTag = "streaming-bubble-assistant",
                    )
                }
            } else {
                item("typing") {
                    Text(
                        "Assistant is typing...",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
