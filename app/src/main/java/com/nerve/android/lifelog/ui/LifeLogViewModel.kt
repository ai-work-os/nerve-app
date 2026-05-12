package com.nerve.android.lifelog.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nerve.android.lifelog.LifeLogConfig
import com.nerve.android.lifelog.LifeLogService
import com.nerve.android.lifelog.UploadQueue
import com.nerve.android.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LifeLogViewModel(app: Application) : AndroidViewModel(app) {
    private val config = LifeLogConfig(app)

    var recording by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var pendingCount by mutableIntStateOf(0)
        private set
    var failedCount by mutableIntStateOf(0)
        private set
    var homeUrl by mutableStateOf(config.homeUrl)
    var token by mutableStateOf(config.token)

    init {
        Logger.debug("LifeLogViewModel", "vm_init", mapOf("homeUrl" to config.homeUrl, "deviceId" to config.deviceId))
        // Poll queue counts every 5 seconds
        viewModelScope.launch {
            while (true) {
                try {
                    val q = UploadQueue(getApplication())
                    pendingCount = q.pendingCount()
                    failedCount = q.failedCount()
                    q.close()
                } catch (e: Exception) {
                    Logger.warn("LifeLogViewModel", "queue_poll_fail", mapOf("reason" to e.message), e)
                }
                delay(5_000)
            }
        }
    }

    fun toggleRecording() {
        val ctx = getApplication<Application>()
        if (!recording) {
            Logger.warn("LifeLogViewModel", "recording_toggle", mapOf("action" to "start"))
            ctx.startForegroundService(
                Intent(ctx, LifeLogService::class.java).setAction(LifeLogService.ACTION_START)
            )
            recording = true
            paused = false
        } else {
            Logger.warn("LifeLogViewModel", "recording_toggle", mapOf("action" to "stop"))
            ctx.startService(
                Intent(ctx, LifeLogService::class.java).setAction(LifeLogService.ACTION_STOP)
            )
            recording = false
            paused = false
        }
    }

    fun togglePause() {
        val ctx = getApplication<Application>()
        val action = if (paused) LifeLogService.ACTION_RESUME else LifeLogService.ACTION_PAUSE
        Logger.debug("LifeLogViewModel", "pause_toggle", mapOf("action" to action))
        ctx.startService(Intent(ctx, LifeLogService::class.java).setAction(action))
        paused = !paused
    }

    fun flushNow() {
        val ctx = getApplication<Application>()
        Logger.warn("LifeLogViewModel", "flush_now", mapOf("ts" to System.currentTimeMillis()))
        ctx.startService(Intent(ctx, LifeLogService::class.java).setAction(LifeLogService.ACTION_FLUSH))
    }

    fun saveSettings() {
        Logger.debug("LifeLogViewModel", "settings_save", mapOf("homeUrl" to homeUrl))
        config.homeUrl = homeUrl
        config.token = token
    }
}
