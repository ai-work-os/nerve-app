package com.nerve.android.screenshot.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nerve.android.screenshot.ScreenshotConfig
import com.nerve.android.screenshot.ScreenshotWatcherService
import com.nerve.android.util.Logger

/**
 * Screenshot watcher settings card.
 * Embeds into the Sense tab (LifeLogScreen) as a sibling section.
 */
@Composable
fun ScreenshotSettings() {
    val ctx = LocalContext.current
    val config = remember { ScreenshotConfig(ctx) }

    var enabled by remember { mutableStateOf(config.enabled) }
    var autoSend by remember { mutableStateOf(config.autoSend) }
    var uploadUrl by remember { mutableStateOf(config.uploadUrl) }
    // Survives configuration changes (rotation) so the denial hint is not lost.
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var showUrlField by remember { mutableStateOf(false) }

    // Build the list of permissions to request
    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        Logger.debug("ScreenshotSettings", "permission_result", mapOf("allGranted" to allGranted))
        if (allGranted) {
            permissionDenied = false
            config.enabled = true
            enabled = true
            ctx.startForegroundService(
                Intent(ctx, ScreenshotWatcherService::class.java).apply {
                    action = ScreenshotWatcherService.ACTION_START
                }
            )
            Logger.debug("ScreenshotSettings", "watcher_started")
        } else {
            permissionDenied = true
            Logger.warn("ScreenshotSettings", "permission_denied")
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "截屏自动发到电脑",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (enabled) "已开启" else "已关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        if (want) {
                            permLauncher.launch(permissions)
                        } else {
                            config.enabled = false
                            enabled = false
                            permissionDenied = false
                            ctx.startService(
                                Intent(ctx, ScreenshotWatcherService::class.java).apply {
                                    action = ScreenshotWatcherService.ACTION_STOP
                                }
                            )
                            Logger.debug("ScreenshotSettings", "watcher_stopped")
                        }
                    },
                )
            }

            // Auto-send toggle — only meaningful while watcher is enabled
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "截屏后自动发送（关闭则每次弹通知确认）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = autoSend,
                    onCheckedChange = { want ->
                        config.autoSend = want
                        autoSend = want
                        Logger.debug("ScreenshotSettings", "auto_send_changed",
                            mapOf("autoSend" to want))
                    },
                )
            }

            if (permissionDenied) {
                Text(
                    "权限被拒绝，截屏监听无法启动。请在系统设置中授权「读取图片」和「发送通知」权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            TextButton(
                onClick = { showUrlField = !showUrlField },
                modifier = Modifier.align(Alignment.Start),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    if (showUrlField) "收起配置" else "服务器地址",
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (showUrlField) {
                OutlinedTextField(
                    value = uploadUrl,
                    onValueChange = { uploadUrl = it },
                    label = { Text("上传地址") },
                    placeholder = { Text(ScreenshotConfig.DEFAULT_UPLOAD_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        config.uploadUrl = uploadUrl
                        showUrlField = false
                        Logger.debug("ScreenshotSettings", "url_saved", mapOf("url" to uploadUrl))
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("保存", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
