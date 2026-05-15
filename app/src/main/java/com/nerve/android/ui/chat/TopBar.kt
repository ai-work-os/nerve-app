package com.nerve.android.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.presentation.chat.ChatUiState
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.RoseAccent

@Composable
fun TopBar(
    state: ChatUiState,
    onCancel: () -> Unit,
    onStop: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    var showStopConfirm by remember { mutableStateOf(false) }

    if (showStopConfirm && onStop != null) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Terminate Node", fontWeight = FontWeight.Black) },
            text = { Text("This will stop the agent process. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    onStop()
                }) { Text("Terminate", color = RoseAccent, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onBack?.let {
                    Surface(
                        onClick = it,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("<", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Column {
                    Text(
                        text = state.nodeName ?: "Unknown Node",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.isStreaming) {
                        Text(
                            text = "responding...",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberPrimary,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isStreaming) {
                    Button(
                        onClick = onCancel,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoseAccent.copy(alpha = 0.1f), contentColor = RoseAccent)
                    ) {
                        Text("STOP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }
                onStop?.let {
                    IconButton(onClick = { showStopConfirm = true }) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = RoseAccent.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("×", fontWeight = FontWeight.Light, fontSize = 20.sp, color = RoseAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}
