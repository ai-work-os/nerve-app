package com.nerve.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.nerve.android.domain.dm.ContentBlock
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.util.Logger

@Composable
fun MessageList(
    messages: List<DmMessage>,
    isStreaming: Boolean,
    streamingText: String,
    streamingBlocks: List<ContentBlock> = emptyList(),
    onAutoScroll: (() -> Unit)? = null,
    onOpenDm: ((String, String, String) -> Unit)? = null,
    failedMessageIds: Set<String> = emptySet(),
    onRetry: (String) -> Unit = {},
) {
    val visibleMessages = remember(messages) {
        messages.mapNotNull { msg ->
            val text = msg.textContent
            val displayText = text.ifBlank { msg.content }
            if (displayText.isBlank()) return@mapNotNull null
            if (msg.role == DmRole.ASSISTANT && displayText != msg.content) msg.copy(content = displayText) else msg
        }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(visibleMessages.size, isStreaming, streamingText, streamingBlocks) {
        val extra = if (isStreaming) 1 else 0
        val target = (visibleMessages.size + extra - 1).coerceAtLeast(0)
        Logger.debug("ChatScreen", "scroll_bottom", mapOf("count" to visibleMessages.size + extra))
        onAutoScroll?.invoke()
        listState.animateScrollToItem(target)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (visibleMessages.isEmpty() && !isStreaming) {
            item {
                Text(
                    "Start the conversation",
                    modifier = Modifier.padding(vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(visibleMessages, key = { it.id }) { message ->
            Column(modifier = Modifier.fillMaxWidth()) {
                MessageBubble(message, onOpenDm = onOpenDm)
                if (message.id in failedMessageIds) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            "✗ 发送失败",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { onRetry(message.id) }) { Text("重试") }
                    }
                }
            }
        }
        if (isStreaming) {
            if (streamingBlocks.isNotEmpty()) {
                item("streaming-blocks") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        streamingBlocks.forEach { block ->
                            when (block) {
                                is ContentBlock.Thinking -> {
                                    ThinkingBlock(block, isLive = !block.completed)
                                }
                                is ContentBlock.ToolCall -> {
                                    Text(
                                        "Tool: ${block.toolName}",
                                        modifier = Modifier
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                is ContentBlock.Text -> {
                                    MessageBubble(
                                        message = DmMessage(
                                            id = "streaming",
                                            role = DmRole.ASSISTANT,
                                            content = block.text,
                                            timestamp = 0L,
                                            nodeId = "",
                                            nodeName = "",
                                        ),
                                        testTag = "streaming-bubble-assistant",
                                        onOpenDm = onOpenDm,
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (streamingText.isNotEmpty()) {
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
                        onOpenDm = onOpenDm,
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

@Composable
private fun ThinkingBlock(block: ContentBlock.Thinking, isLive: Boolean) {
    var expanded by remember { mutableStateOf(isLive) }
    // Auto-expand while streaming
    LaunchedEffect(isLive) {
        if (isLive) expanded = true
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            if (expanded) "Thinking..." else "Thinking... (tap to expand)",
            style = MaterialTheme.typography.labelMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            Text(
                block.text,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
