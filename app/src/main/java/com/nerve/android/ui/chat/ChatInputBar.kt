package com.nerve.android.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.CreamBackground

@Composable
fun ChatInputBar(
    canSend: Boolean,
    isSending: Boolean,
    isStreaming: Boolean = false,
    onSend: (String, List<PendingAttachment>) -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit = {},
    pendingAttachments: List<PendingAttachment> = emptyList(),
    onRemoveAttachment: (Int) -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf("") }
    val enabled = ChatInputState.isSendEnabled(
        text = text,
        hasAttachment = pendingAttachments.isNotEmpty(),
        canSend = canSend,
        isSending = isSending,
        isStreaming = isStreaming,
    )
    val emitSend: () -> Unit = {
        if (enabled) {
            val value = text
            val attachments = pendingAttachments
            text = ""
            onSend(value, attachments)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pendingAttachments.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.2f)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pendingAttachments.forEachIndexed { index, attachment ->
                        Icon(
                            if (attachment.kind == Kind.IMAGE) Icons.Default.Image else Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = attachment.displayName ?: if (attachment.kind == Kind.IMAGE) "Image ${index + 1}" else "File ${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { onRemoveAttachment(index) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove attachment ${index + 1}", modifier = Modifier.size(14.dp))
                        }
                        if (index != pendingAttachments.lastIndex) {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            }
        }
        
        Surface(
            modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            color = Color.White.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onPickImage,
                    enabled = !isSending,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Upload image", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                IconButton(
                    onClick = onPickFile,
                    enabled = !isSending,
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = "Upload file", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Issue instruction...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSend = { emitSend() },
                    ),
                )

                Button(
                    onClick = emitSend,
                    enabled = enabled,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberPrimary,
                        disabledContainerColor = AmberPrimary.copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, 
                        contentDescription = "Send", 
                        tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
