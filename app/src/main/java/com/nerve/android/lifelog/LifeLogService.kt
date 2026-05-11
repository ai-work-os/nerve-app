package com.nerve.android.lifelog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nerve.android.MainActivity
import com.nerve.android.R
import okhttp3.OkHttpClient
import java.io.File

private const val TAG = "LifeLogService"

class LifeLogService : Service() {
    companion object {
        const val CHANNEL_ID = "lifelog_recording"
        const val NOTI_ID = 1042
        const val ACTION_START = "com.nerve.android.lifelog.START"
        const val ACTION_PAUSE = "com.nerve.android.lifelog.PAUSE"
        const val ACTION_RESUME = "com.nerve.android.lifelog.RESUME"
        const val ACTION_FLUSH = "com.nerve.android.lifelog.FLUSH"
        const val ACTION_STOP = "com.nerve.android.lifelog.STOP"
        const val CHUNK_DURATION_MS = 60_000L
    }

    private lateinit var config: LifeLogConfig
    private lateinit var queue: UploadQueue
    private lateinit var chunkWriter: ChunkWriter
    private lateinit var uploader: Uploader
    private lateinit var policy: SyncPolicy
    private lateinit var recorder: AudioRecorder
    private var encoder: OpusEncoder? = null
    private var chunkStartedAt: Long = 0
    private var paused = false
    private var pendingChunkFile: File? = null
    private var tickThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        config = LifeLogConfig(this)
        queue = UploadQueue(this)
        chunkWriter = ChunkWriter(this, config.deviceId)
        val client = OkHttpClient()
        uploader = Uploader(client, queue, config.homeUrl, config.token)
        policy = SyncPolicy(
            flush = { uploader.flushAll() },
            isUnmeteredNow = { isUnmeteredNetwork(this) },
        )
        recorder = AudioRecorder()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_PAUSE -> {
                paused = true
                updateNotification("已暂停")
                Log.i(TAG, "recording paused")
            }
            ACTION_RESUME -> {
                paused = false
                updateNotification("正在录音")
                Log.i(TAG, "recording resumed")
            }
            ACTION_FLUSH -> {
                Thread {
                    Log.i(TAG, "manual flush triggered")
                    val sent = policy.flushNow()
                    Log.i(TAG, "manual flush done: sent=$sent")
                }.also { it.name = "lifelog-flush-thread"; it.start() }
            }
            ACTION_STOP -> {
                stopRecordingAndSelf()
                return START_NOT_STICKY
            }
            else -> startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (encoder != null) {
            Log.i(TAG, "startRecording: already running, ignoring")
            return
        }
        Log.i(TAG, "startRecording: homeUrl=${config.homeUrl} deviceId=${config.deviceId}")
        startForeground(NOTI_ID, buildNotification("正在录音"))
        rotateChunk(initial = true)
        recorder.start { pcm, len ->
            if (paused) return@start
            encoder?.feed(pcm, len)
            if (System.currentTimeMillis() - chunkStartedAt >= CHUNK_DURATION_MS) {
                Log.i(TAG, "chunk boundary reached, rotating")
                rotateChunk(initial = false)
            }
        }
        // tickAuto loop — runs every 30s while recording
        val t = Thread {
            while (encoder != null) {
                try { Thread.sleep(30_000) } catch (_: InterruptedException) { return@Thread }
                if (!paused) {
                    Log.i(TAG, "tickAuto: checking sync policy")
                    policy.tickAuto()
                }
            }
        }
        t.name = "lifelog-tick-thread"
        t.isDaemon = true
        t.start()
        tickThread = t
        Log.i(TAG, "recording started")
    }

    @Synchronized
    private fun rotateChunk(initial: Boolean) {
        val now = System.currentTimeMillis()
        val finishedFile = pendingChunkFile
        val finishedStart = chunkStartedAt
        val finishedDuration = if (initial) 0L else now - finishedStart

        if (!initial) {
            try {
                encoder?.finishToFile()
            } catch (e: Exception) {
                Log.e(TAG, "finishToFile failed", e)
            }
            if (finishedFile != null && finishedFile.exists() && finishedDuration > 0) {
                try {
                    val bytes = finishedFile.readBytes()
                    val chunk = chunkWriter.write(bytes, finishedStart, finishedDuration)
                    queue.enqueue(chunk)
                    finishedFile.delete()
                    Log.i(TAG, "chunk rotated: id=${chunk.chunkId} duration=${finishedDuration}ms size=${bytes.size}B")
                } catch (e: Exception) {
                    Log.e(TAG, "chunk write/enqueue failed", e)
                }
            }
        }

        val nextFile = File(cacheDir, "lifelog/encoding_${now}.ogg").apply { parentFile?.mkdirs() }
        encoder = OpusEncoder(nextFile)
        pendingChunkFile = nextFile
        chunkStartedAt = now
        Log.i(TAG, "rotateChunk: initial=$initial nextFile=${nextFile.name}")
    }

    private fun stopRecordingAndSelf() {
        Log.i(TAG, "stopRecordingAndSelf")
        tickThread?.interrupt()
        tickThread = null
        val enc = encoder
        encoder = null
        try {
            enc?.finishToFile()
        } catch (e: Exception) {
            Log.w(TAG, "finishToFile on stop", e)
        }
        recorder.stop()
        pendingChunkFile?.delete()
        pendingChunkFile = null
        try { queue.close() } catch (e: Exception) { Log.w(TAG, "queue.close", e) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "service stopped")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Life Log Recording", NotificationManager.IMPORTANCE_LOW)
            )
            Log.i(TAG, "notification channel created: $CHANNEL_ID")
        }
    }

    private fun buildNotification(title: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()

    private fun updateNotification(title: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTI_ID, buildNotification(title))
        Log.i(TAG, "notification updated: $title")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        stopRecordingAndSelf()
        super.onDestroy()
    }
}

/**
 * Returns true when the device has an active unmetered network (typically Wi-Fi).
 * Used by SyncPolicy to decide whether to auto-upload chunks.
 */
fun isUnmeteredNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
