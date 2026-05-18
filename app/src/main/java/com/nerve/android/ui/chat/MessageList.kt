package com.nerve.android.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.domain.dm.ContentBlock
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.ui.theme.AmberPrimary
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
        messages.filter { it.textContent.isNotBlank() || it.blocks.isNotEmpty() }
    }
    val listState = rememberLazyListState()
    var hasScrolledToBottomInitially by remember { mutableStateOf(false) }

    // Initial scroll to bottom
    LaunchedEffect(visibleMessages.isNotEmpty()) {
        if (visibleMessages.isNotEmpty() && !hasScrolledToBottomInitially) {
            val extra = if (isStreaming) 1 else 0
            val target = (visibleMessages.size + extra - 1).coerceAtLeast(0)
            listState.scrollToItem(target)
            hasScrolledToBottomInitially = true
        }
    }

    // Auto-scroll for new messages or streaming
    LaunchedEffect(visibleMessages.size, isStreaming, streamingText, streamingBlocks) {
        if (!hasScrolledToBottomInitially) return@LaunchedEffect
        
        val extra = if (isStreaming) 1 else 0
        val target = (visibleMessages.size + extra - 1).coerceAtLeast(0)
        
        val isAtBottom = if (listState.layoutInfo.visibleItemsInfo.isEmpty()) true 
                        else listState.layoutInfo.visibleItemsInfo.last().index >= listState.layoutInfo.totalItemsCount - 2
        
        if (isAtBottom || isStreaming) {
            onAutoScroll?.invoke()
            listState.animateScrollToItem(target)
        }
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
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        
        items(visibleMessages, key = { it.id }) { message ->
            MessageContent(message, onOpenDm, isFailed = message.id in failedMessageIds, onRetry = onRetry)
        }
        
        if (isStreaming) {
            item("streaming") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (streamingBlocks.isNotEmpty()) {
                        streamingBlocks.forEach { block ->
                            RenderBlock(block, isStreaming = true, onOpenDm = onOpenDm)
                        }
                    } else if (streamingText.isNotEmpty()) {
                        MessageBubble(
                            message = DmMessage("streaming", DmRole.ASSISTANT, streamingText, 0, "", ""),
                            onOpenDm = onOpenDm
                        )
                    } else {
                        Text(
                            "Assistant is typing...",
                            modifier = Modifier.padding(horizontal = 60.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContent(
    message: DmMessage,
    onOpenDm: ((String, String, String) -> Unit)?,
    isFailed: Boolean,
    onRetry: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.blocks.isNotEmpty()) {
            message.blocks.forEachIndexed { index, block ->
                RenderBlock(
                    block = block,
                    isStreaming = false,
                    onOpenDm = onOpenDm,
                    forceRole = message.role,
                    forceName = message.nodeName,
                    showAvatar = index == 0
                )
            }
        } else {
            MessageBubble(message, onOpenDm = onOpenDm)
        }
        
        if (isFailed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Transmission failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onRetry(message.id) }, contentPadding = PaddingValues(0.dp)) {
                    Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RenderBlock(
    block: ContentBlock,
    isStreaming: Boolean = false,
    onOpenDm: ((String, String, String) -> Unit)? = null,
    forceRole: DmRole = DmRole.ASSISTANT,
    forceName: String = "",
    showAvatar: Boolean = true
) {
    when (block) {
        is ContentBlock.Text -> {
            MessageBubble(
                message = DmMessage("blk", forceRole, block.text, 0, "", forceName),
                onOpenDm = onOpenDm,
                showAvatar = showAvatar
            )
        }
        is ContentBlock.Thinking -> {
            ThinkingBlock(block, isLive = isStreaming && !block.completed)
        }
        is ContentBlock.ToolCall -> {
            ToolCallBlock(block)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThinkingBlock(block: ContentBlock.Thinking, isLive: Boolean) {
    var expanded by remember { mutableStateOf(isLive) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isLive) { if (isLive) expanded = true }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp, end = 24.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("thinking", block.text))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "Thinking..." else "Thought (tap to expand)",
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (isLive) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp, color = AmberPrimary)
            }
        }
        if (expanded && block.text.isNotBlank()) {
            Text(
                text = block.text,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCallBlock(block: ContentBlock.ToolCall) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp, end = 24.dp, top = 2.dp, bottom = 2.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("tool", block.toolName))
                    Toast.makeText(context, "Tool name copied", Toast.LENGTH_SHORT).show()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = AmberPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Tool: ${block.toolName}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
