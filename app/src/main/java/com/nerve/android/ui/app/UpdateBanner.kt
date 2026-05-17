package com.nerve.android.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nerve.android.update.AppVersionInfo
import com.nerve.android.update.DownloadState
import com.nerve.android.ui.theme.AmberPrimary
import com.nerve.android.ui.theme.StoneMain

@Composable
fun UpdateBanner(
    info: AppVersionInfo,
    download: DownloadState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = StoneMain,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System Update Available",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = AmberPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Version v${info.versionName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    val statusText = when (download) {
                        is DownloadState.Idle -> info.notes?.takeIf { it.isNotBlank() } ?: "New features and optimizations"
                        is DownloadState.InProgress -> {
                            val mb = download.downloaded / 1_000_000
                            val totalMb = (download.total.takeIf { it > 0 } ?: 0) / 1_000_000
                            if (totalMb > 0) "Synchronizing: ${mb}MB / ${totalMb}MB" else "Synchronizing: ${mb}MB"
                        }
                        is DownloadState.Ready -> "Extraction complete. Ready to install."
                        is DownloadState.Failed -> "Transmission failed: ${download.reason ?: "Unknown"}"
                    }
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }

                when (download) {
                    is DownloadState.Idle -> {
                        TextButton(onClick = onDismiss) { 
                            Text("Later", color = Color.White.copy(alpha = 0.5f)) 
                        }
                        Button(
                            onClick = onUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Text("Update", fontWeight = FontWeight.Black, color = StoneMain)
                        }
                    }
                    is DownloadState.InProgress -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AmberPrimary,
                            strokeWidth = 3.dp
                        )
                    }
                    is DownloadState.Ready -> {
                        Button(
                            onClick = onUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Install", fontWeight = FontWeight.Black, color = StoneMain)
                        }
                    }
                    is DownloadState.Failed -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberPrimary)
                        }
                    }
                }
            }
            
            if (download is DownloadState.InProgress) {
                val ratio = if (download.total > 0) {
                    (download.downloaded.toFloat() / download.total).coerceIn(0f, 1f)
                } else null
                
                if (ratio != null) {
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = AmberPrimary,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}
