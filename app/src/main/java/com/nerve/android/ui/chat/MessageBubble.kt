package com.nerve.android.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nerve.android.domain.dm.DmAction
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.StoneMain
import com.nerve.android.ui.theme.WhiteSurface
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.CompositionLocalProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MarkdownText(content: String, modifier: Modifier = Modifier) {
    val textColor = LocalContentColor.current.toArgb()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val markwon = Markwon.builder(context)
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(object : io.noties.markwon.AbstractMarkwonPlugin() {
                    override fun configureTheme(builder: MarkwonTheme.Builder) {
                        builder.codeTypeface(Typeface.MONOSPACE)
                        builder.codeBlockTypeface(Typeface.MONOSPACE)
                    }
                })
                .build()
            TextView(context).apply {
                setTextColor(textColor)
                tag = markwon
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            val markwon = textView.tag as Markwon
            markwon.setMarkdown(textView, content)
        },
    )
}

@Composable
private fun SmallAvatar(label: String, isUser: Boolean) {
    val bg = if (isUser) AmberPrimary else StoneMain
    val fg = if (isUser) Color.White else AmberPrimary
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(2).uppercase(),
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: DmMessage,
    testTag: String? = null,
    onOpenDm: ((String, String, String) -> Unit)? = null,
    showAvatar: Boolean = true,
) {
    val isUser = message.role == DmRole.USER
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val visibleContent = message.textContent.ifBlank { message.content }
    val copyMessage = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", visibleContent))
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            if (showAvatar) {
                SmallAvatar(label = message.nodeName.ifEmpty { "AI" }, isUser = false)
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Header Info (Only show if avatar is shown)
            if (showAvatar) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "Commander" else message.nodeName.ifEmpty { "Assistant" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (message.timestamp > 0) formatTime(message.timestamp) else "just now",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp).let {
                    if (isUser) it.copy(topEnd = CornerSize(0.dp)) else it.copy(topStart = CornerSize(0.dp))
                },
                color = if (isUser) StoneMain else WhiteSurface,
                shadowElevation = if (isUser) 4.dp else 1.dp,
                border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.1f)) else null,
                modifier = Modifier
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val action = message.action
                        CompositionLocalProvider(
                            LocalContentColor provides if (isUser) AmberPrimary else MaterialTheme.colorScheme.onSurface
                        ) {
                            if (action == null) {
                                MarkdownText(visibleContent)
                            } else {
                                Text(visibleContent, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (action is DmAction.OpenDm && onOpenDm != null) {
                            Button(
                                onClick = { onOpenDm(action.serverId, action.nodeId, action.nodeName) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                            ) {
                                Text("Enter DM", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(12.dp))
            SmallAvatar(label = "CM", isUser = true)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}
