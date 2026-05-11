package com.nerve.android.lifelog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LifeLogScreen(vm: LifeLogViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Life Log", style = MaterialTheme.typography.headlineMedium)

        val statusText = when {
            !vm.recording -> "未启动"
            vm.paused -> "已暂停"
            else -> "正在录音"
        }
        Text(statusText, style = MaterialTheme.typography.titleLarge)

        Switch(
            checked = vm.recording,
            onCheckedChange = { vm.toggleRecording() },
        )

        if (vm.recording) {
            Button(onClick = { vm.togglePause() }) {
                Text(if (vm.paused) "继续" else "暂停")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("待上传：${vm.pendingCount}")
                Text("失败：${vm.failedCount}")
            }
        }

        Button(
            onClick = { vm.flushNow() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("立即上传（任何网络）")
        }

        HorizontalDivider()
        Text("设置", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = vm.homeUrl,
            onValueChange = { vm.homeUrl = it },
            label = { Text("home_url（如 http://100.x.x.x:4810）") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.token,
            onValueChange = { vm.token = it },
            label = { Text("token") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.saveSettings() }) {
            Text("保存设置")
        }
    }
}
