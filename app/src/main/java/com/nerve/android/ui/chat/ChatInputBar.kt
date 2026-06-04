package com.nerve.android.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nerve.android.ui.theme.AmberPrimary

@Composable
fun ChatInputBar(
    canSend: Boolean,
    isSending: Boolean,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
    onSend: (String, List<PendingAttachment>) -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit = {},
    pendingAttachments: List<PendingAttachment> = emptyList(),
    onRemoveAttachment: (Int) -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf("") }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AttachmentChipRow(pendingAttachments, onRemoveAttachment)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                    shadowElevation = 5.dp,
                ) {
                    IconButton(
                        onClick = { attachmentMenuExpanded = true },
                        enabled = !isSending,
                        modifier = Modifier.size(46.dp),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Open attachment menu",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                DropdownMenu(
                    expanded = attachmentMenuExpanded,
                    onDismissRequest = { attachmentMenuExpanded = false },
                    modifier = Modifier.width(220.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("照片", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        },
                        onClick = {
                            attachmentMenuExpanded = false
                            onPickImage()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("文件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        },
                        onClick = {
                            attachmentMenuExpanded = false
                            onPickFile()
                        },
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = Color.White.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                shadowElevation = 5.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "问问 Nerve",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
                            )
                        },
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
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            disabledContainerColor = AmberPrimary.copy(alpha = 0.16f),
                        ),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (enabled) Color.White else AmberPrimary.copy(alpha = 0.45f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChipRow(
    pendingAttachments: List<PendingAttachment>,
    onRemoveAttachment: (Int) -> Unit,
) {
    if (pendingAttachments.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pendingAttachments.forEachIndexed { index, attachment ->
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.18f)),
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (attachment.kind == Kind.IMAGE) Icons.Default.Image else Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = attachment.displayName ?: if (attachment.kind == Kind.IMAGE) "Image ${index + 1}" else "File ${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { onRemoveAttachment(index) }, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove attachment ${index + 1}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
