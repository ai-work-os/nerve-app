package com.nerve.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerve.android.presentation.chat.ChatUiState

@Composable
fun TopBar(
    state: ChatUiState,
    onCancel: () -> Unit,
    onStop: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let {
                TextButton(onClick = it) { Text("Back") }
            }
            Column {
                Text(state.nodeName ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                if (state.isStreaming) {
                    Text("responding...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.isStreaming) {
                TextButton(onClick = onCancel) {
                    Text("Stop")
                }
            }
            onStop?.let {
                TextButton(onClick = it) {
                    Text("Close")
                }
            }
        }
    }
}
