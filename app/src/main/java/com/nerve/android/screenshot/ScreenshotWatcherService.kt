package com.nerve.android.screenshot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.nerve.android.R
import com.nerve.android.util.Logger
import okhttp3.OkHttpClient

class ScreenshotWatcherService : Service() {

    companion object {
        const val CHANNEL_ID = "screenshot_watcher"
        const val NOTI_ID = 2001       // persistent foreground notification id
        const val ACTION_START = "com.nerve.android.screenshot.START"
        const val ACTION_SEND = "com.nerve.android.screenshot.SEND"
        const val ACTION_DISMISS = "com.nerve.android.screenshot.DISMISS"
        const val ACTION_STOP = "com.nerve.android.screenshot.STOP"
        const val EXTRA_URI = "screenshot_uri"
        const val EXTRA_NOTIF_ID = "screenshot_notif_id"
    }

    private lateinit var config: ScreenshotConfig
    private lateinit var uploader: ScreenshotUploader
    private lateinit var seenUris: SeenUris
    private var serviceStartedAtSec: Long = 0L

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri == null) return
            Logger.debug("ScreenshotWatcherService", "observer_change", mapOf("uri" to uri.toString()))
            Thread { checkForScreenshot(uri) }.also {
                it.name = "screenshot-check-thread"
                it.isDaemon = true
                it.start()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.debug("ScreenshotWatcherService", "service_create")
        config = ScreenshotConfig(this)
        uploader = ScreenshotUploader(OkHttpClient())
        seenUris = SeenUris()
        serviceStartedAtSec = System.currentTimeMillis() / 1000
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.debug("ScreenshotWatcherService", "intent_received", mapOf("action" to intent?.action))
        when (intent?.action) {
            ACTION_SEND -> {
                val uriStr = intent.getStringExtra(EXTRA_URI) ?: return START_STICKY
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                Thread {
                    handleSend(uriStr, notifId)
                }.also { it.name = "screenshot-upload-thread"; it.isDaemon = true; it.start() }
            }
            ACTION_DISMISS -> {
                val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
                if (notifId >= 0) {
                    getSystemService(NotificationManager::class.java).cancel(notifId)
                    Logger.debug("ScreenshotWatcherService", "screenshot_dismissed", mapOf("notifId" to notifId))
                }
            }
            ACTION_STOP -> {
                stopWatcher()
                return START_NOT_STICKY
            }
            else -> startWatcher()
        }
        return START_STICKY
    }

    private fun startWatcher() {
        Logger.debug("ScreenshotWatcherService", "watcher_start", mapOf("uploadUrl" to config.uploadUrl))
        startForeground(NOTI_ID, buildPersistentNotification())
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        Logger.debug("ScreenshotWatcherService", "observer_registered")
    }

    private fun stopWatcher() {
        Logger.debug("ScreenshotWatcherService", "watcher_stop")
        contentResolver.unregisterContentObserver(observer)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Logger.debug("ScreenshotWatcherService", "watcher_stopped")
    }

    private fun checkForScreenshot(uri: Uri) {
        val uriStr = uri.toString()
        if (!seenUris.firstTimeFor(uriStr)) {
            Logger.debug("ScreenshotWatcherService", "uri_duplicate_skip", mapOf("uri" to uriStr))
            return
        }

        val projection = arrayOf(
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media._ID,
        )

        // Try to query the specific uri first, fall back to last inserted
        val queryUri = if (uri.toString().contains("images/media/") && uri.lastPathSegment?.toLongOrNull() != null) {
            uri
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        contentResolver.query(queryUri, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cursor ->
            if (!cursor.moveToFirst()) return@use

            val relativePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
            val bucketName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
            val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))

            Logger.debug("ScreenshotWatcherService", "image_query_result", mapOf(
                "relativePath" to relativePath,
                "bucketName" to bucketName,
                "dateAdded" to dateAdded,
                "serviceStartedAt" to serviceStartedAtSec,
                "mimeType" to mimeType,
            ))

            // Only accept images added after service started, and only screenshots
            if (dateAdded < serviceStartedAtSec) {
                Logger.debug("ScreenshotWatcherService", "image_too_old_skip", mapOf("dateAdded" to dateAdded))
                return@use
            }

            if (!isScreenshot(relativePath, bucketName)) {
                Logger.debug("ScreenshotWatcherService", "not_screenshot_skip", mapOf("relativePath" to relativePath))
                return@use
            }

            // Build the content URI for the specific image
            val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            val imageUriStr = imageUri.toString()

            // Deduplicate by content URI
            if (!seenUris.firstTimeFor(imageUriStr)) {
                Logger.debug("ScreenshotWatcherService", "content_uri_duplicate_skip", mapOf("uri" to imageUriStr))
                return@use
            }

            Logger.warn("ScreenshotWatcherService", "screenshot_detected", mapOf(
                "uri" to imageUriStr, "relativePath" to relativePath, "mime" to mimeType,
            ))

            postScreenshotNotification(imageUriStr, mimeType)
        }
    }

    private fun postScreenshotNotification(uriStr: String, mimeType: String) {
        val notifId = NOTI_ID + (uriStr.hashCode() and 0x7FFFFFFF) + 1

        val sendIntent = Intent(this, ScreenshotWatcherService::class.java).apply {
            action = ACTION_SEND
            putExtra(EXTRA_URI, uriStr)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val sendPi = PendingIntent.getService(
            this, notifId,
            sendIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val dismissIntent = Intent(this, ScreenshotWatcherService::class.java).apply {
            action = ACTION_DISMISS
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val dismissPi = PendingIntent.getService(
            this, notifId + 1,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("检测到截图")
            .setAutoCancel(true)
            .addAction(0, "发到电脑", sendPi)
            .addAction(0, "忽略", dismissPi)
            .build()

        getSystemService(NotificationManager::class.java).notify(notifId, notif)
        Logger.debug("ScreenshotWatcherService", "screenshot_notif_posted", mapOf("notifId" to notifId, "uri" to uriStr))
    }

    private fun handleSend(uriStr: String, notifId: Int) {
        val uri = Uri.parse(uriStr)
        Logger.warn("ScreenshotWatcherService", "upload_start", mapOf("uri" to uriStr))

        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Logger.warn("ScreenshotWatcherService", "read_bytes_fail", mapOf("reason" to e.message), e)
            null
        }

        if (bytes == null) {
            Logger.warn("ScreenshotWatcherService", "upload_abort_no_bytes", mapOf("uri" to uriStr))
            return
        }

        val mime = contentResolver.getType(uri) ?: "image/png"
        val takenAtMs = System.currentTimeMillis()

        val ok = uploader.upload(
            baseUrl = config.uploadUrl,
            imageBytes = bytes,
            mimeType = mime,
            source = config.deviceName,
            analyze = false,
            takenAtMs = takenAtMs,
        )

        if (ok) {
            getSystemService(NotificationManager::class.java).cancel(notifId)
            Logger.warn("ScreenshotWatcherService", "upload_success_notif_cancelled", mapOf("notifId" to notifId))
        } else {
            Logger.warn("ScreenshotWatcherService", "upload_failed", mapOf("notifId" to notifId, "uri" to uriStr))
            // Update notification to show failure
            val retryIntent = Intent(this, ScreenshotWatcherService::class.java).apply {
                action = ACTION_SEND
                putExtra(EXTRA_URI, uriStr)
                putExtra(EXTRA_NOTIF_ID, notifId)
            }
            val retryPi = PendingIntent.getService(
                this, notifId,
                retryIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val failNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("发送失败，点击重试")
                .setContentIntent(retryPi)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(notifId, failNotif)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "截屏监听", NotificationManager.IMPORTANCE_LOW)
            )
            Logger.debug("ScreenshotWatcherService", "notif_channel_created", mapOf("id" to CHANNEL_ID))
        }
    }

    private fun buildPersistentNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在监听截图")
            .setOngoing(true)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.debug("ScreenshotWatcherService", "service_destroy")
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }
}
