package com.nerve.android.ui.chat

import android.graphics.Typeface
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
fun MessageBubble(message: DmMessage, testTag: String? = null) {
    val arrangement = when (message.role) {
        DmRole.USER -> Arrangement.End
        DmRole.ASSISTANT -> Arrangement.Start
        DmRole.SYSTEM -> Arrangement.Center
    }
    val background = when (message.role) {
        DmRole.USER -> MaterialTheme.colorScheme.primaryContainer
        DmRole.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
        DmRole.SYSTEM -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
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
                    .background(background, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                MarkdownText(message.content)
            }
        }
    }
}
