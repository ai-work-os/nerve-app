package com.nerve.android.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppTopBar(
    title: String = "Nerve",
    onBack: (() -> Unit)? = null,
    onOpenServers: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) {
                Button(onClick = onBack) {
                    Text("Back")
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Button(onClick = onOpenServers) {
            Text("Servers")
        }
    }
}
