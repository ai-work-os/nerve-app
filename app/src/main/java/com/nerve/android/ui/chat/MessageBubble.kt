package com.nerve.android.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nerve.android.domain.dm.DmMessage
import com.nerve.android.domain.dm.DmRole
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin

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
                    override fun configureSpansFactory(builder: io.noties.markwon.MarkwonSpansFactory.Builder) {
                        // Apply text color to all rendered spans
                    }
                })
                .build()
            TextView(context).apply {
                setTextColor(textColor)
                tag = markwon
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
private fun Avatar(label: String, isUser: Boolean) {
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val fg = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1).uppercase(),
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: DmMessage, testTag: String? = null) {
    val isUser = message.role == DmRole.USER
    val context = LocalContext.current
    val background = when (message.role) {
        DmRole.USER -> MaterialTheme.colorScheme.primaryContainer
        DmRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
        DmRole.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Avatar(label = message.nodeName.ifEmpty { "A" }, isUser = false)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            if (message.role == DmRole.ASSISTANT && message.nodeName.isNotEmpty()) {
                Text(
                    message.nodeName,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                    )
                    .background(background, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                MarkdownText(message.content)
            }
        }
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Avatar(label = "U", isUser = true)
        }
    }
}
