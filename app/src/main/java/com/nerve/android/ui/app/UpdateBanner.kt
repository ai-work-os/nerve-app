package com.nerve.android.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerve.android.update.AppVersionInfo
import com.nerve.android.update.DownloadState

@Composable
fun UpdateBanner(
    info: AppVersionInfo,
    download: DownloadState,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "新版本 v${info.versionName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                val subtitle = when (download) {
                    is DownloadState.Idle -> info.notes?.takeIf { it.isNotBlank() }
                    is DownloadState.InProgress -> {
                        val mb = download.downloaded / 1_000_000
                        val totalMb = (download.total.takeIf { it > 0 } ?: 0) / 1_000_000
                        if (totalMb > 0) "下载中 ${mb}MB / ${totalMb}MB" else "下载中 ${mb}MB"
                    }
                    is DownloadState.Ready -> "下载完成，启动安装"
                    is DownloadState.Failed -> "下载失败：${download.reason ?: "未知错误"}"
                }
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            when (download) {
                is DownloadState.Idle -> {
                    TextButton(onClick = onDismiss) { Text("稍后") }
                    Button(onClick = onUpdate) { Text("更新") }
                }
                is DownloadState.InProgress -> {
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
                is DownloadState.Ready -> {
                    Button(onClick = onUpdate) { Text("安装") }
                }
                is DownloadState.Failed -> {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                    Button(onClick = onRetry) { Text("重试") }
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
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
