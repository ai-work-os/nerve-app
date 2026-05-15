package com.nerve.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ChatInputBar(
    canSend: Boolean,
    isSending: Boolean,
    onSend: (String) -> Unit,
    onPickImage: () -> Unit,
    pendingAttachment: PendingAttachment? = null,
    onClearAttachment: () -> Unit = {},
) {
    var text by rememberSaveable { mutableStateOf("") }
    val enabled = ChatInputState.isSendEnabled(
        text = text,
        hasAttachment = pendingAttachment != null,
        canSend = canSend,
        isSending = isSending,
    )
    val emitSend: () -> Unit = {
        if (enabled) {
            val value = text
            text = ""
            onSend(value)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (pendingAttachment != null) {
            AssistChip(
                onClick = onClearAttachment,
                label = {
                    Text(pendingAttachment.displayName ?: pendingAttachment.mimeType)
                },
                leadingIcon = {
                    Icon(Icons.Default.Image, contentDescription = null)
                },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = "Remove attachment")
                },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                label = { Text("Message") },
                maxLines = 5,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Default),
            )
            IconButton(
                onClick = onPickImage,
                enabled = canSend && !isSending,
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Icon(Icons.Default.Image, contentDescription = "Upload image")
            }
            IconButton(
                onClick = emitSend,
                enabled = enabled,
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
