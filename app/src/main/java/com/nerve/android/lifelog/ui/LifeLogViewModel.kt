package com.nerve.android.lifelog.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nerve.android.lifelog.LifeLogConfig
import com.nerve.android.lifelog.LifeLogService
import com.nerve.android.lifelog.UploadQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "LifeLogViewModel"

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
        Log.i(TAG, "init: homeUrl=${config.homeUrl} deviceId=${config.deviceId}")
        // Poll queue counts every 5 seconds
        viewModelScope.launch {
            while (true) {
                try {
                    val q = UploadQueue(getApplication())
                    pendingCount = q.pendingCount()
                    failedCount = q.failedCount()
                    q.close()
                } catch (e: Exception) {
                    Log.w(TAG, "queue poll failed", e)
                }
                delay(5_000)
            }
        }
    }

    fun toggleRecording() {
        val ctx = getApplication<Application>()
        if (!recording) {
            Log.i(TAG, "toggleRecording: starting service")
            ctx.startForegroundService(
                Intent(ctx, LifeLogService::class.java).setAction(LifeLogService.ACTION_START)
            )
            recording = true
            paused = false
        } else {
            Log.i(TAG, "toggleRecording: stopping service")
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
        Log.i(TAG, "togglePause: action=$action")
        ctx.startService(Intent(ctx, LifeLogService::class.java).setAction(action))
        paused = !paused
    }

    fun flushNow() {
        val ctx = getApplication<Application>()
        Log.i(TAG, "flushNow: sending FLUSH intent")
        ctx.startService(Intent(ctx, LifeLogService::class.java).setAction(LifeLogService.ACTION_FLUSH))
    }

    fun saveSettings() {
        Log.i(TAG, "saveSettings: homeUrl=$homeUrl")
        config.homeUrl = homeUrl
        config.token = token
    }
}
