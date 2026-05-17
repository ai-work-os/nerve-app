package com.nerve.android.screenshot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.nerve.android.MainActivity
import com.nerve.android.R
import com.nerve.android.util.Logger
import okhttp3.OkHttpClient
import kotlin.math.abs

/**
 * Foreground service that watches MediaStore for new screenshots and either
 * uploads them automatically (autoSend=true) or offers a confirm notification.
 *
 * Detection uses a 3-second polling loop rather than ContentObserver because
 * ColorOS/OPPO (Android 16) freezes the app and stops delivering onChange
 * callbacks while the foreground service is still alive.
 *
 * NOTE — architectural limit: this runs as a `dataSync` foreground service. On
 * API 34+ the OS force-stops a `dataSync` FGS after ~6 hours of cumulative
 * runtime (see [onTimeout]). When that happens monitoring stops; we surface a
 * user-visible notification so the death is visible rather than silent. A full
 * fix (e.g. periodic WorkManager re-scan) is out of scope for Phase 1.
 */
class ScreenshotWatcherService : Service() {

    companion object {
        const val CHANNEL_ID = "screenshot_watcher"
        const val CHANNEL_ALERTS_ID = "screenshot_alerts"
        const val NOTI_ID = 2001       // persistent foreground notification id
        const val ACTION_START = "com.nerve.android.screenshot.START"
        const val ACTION_SEND = "com.nerve.android.screenshot.SEND"
        const val ACTION_DISMISS = "com.nerve.android.screenshot.DISMISS"
        const val ACTION_STOP = "com.nerve.android.screenshot.STOP"
        const val EXTRA_URI = "screenshot_uri"
        const val EXTRA_NOTIF_ID = "screenshot_notif_id"

        // Shared id for the "sent" success toast — fixed so each new success
        // replaces the previous one (no pile-up). Sits outside the per-image
        // notifId range [NOTI_ID+1, NOTI_ID+50001].
        const val SUCCESS_NOTI_ID = NOTI_ID + 60_000

        private const val POLL_INTERVAL_MS = 3_000L
    }

    private lateinit var config: ScreenshotConfig
    private lateinit var uploader: ScreenshotUploader
    private lateinit var seenUris: SeenUris
    private var serviceStartedAtSec: Long = 0L
    private var pollingActive = false
    private var pollThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        Logger.debug("ScreenshotWatcherService", "service_create")
        config = ScreenshotConfig(this)
        uploader = ScreenshotUploader(OkHttpClient())
        seenUris = SeenUris()
        serviceStartedAtSec = System.currentTimeMillis() / 1000
        createChannels()
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
        // startForeground must run on every onStartCommand to satisfy the FGS contract.
        startForeground(NOTI_ID, buildPersistentNotification())
        // Idempotency guard: a rapid toggle / repeated startForegroundService delivers
        // another onStartCommand; without this we'd start a second polling thread.
        if (pollingActive) {
            Logger.debug("ScreenshotWatcherService", "watcher_already_running")
            return
        }
        Logger.debug("ScreenshotWatcherService", "watcher_start",
            mapOf("uploadUrl" to config.uploadUrl, "pollIntervalMs" to POLL_INTERVAL_MS))
        pollingActive = true

        val t = Thread {
            Logger.debug("ScreenshotWatcherService", "poll_loop_started")
            while (pollingActive && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (pollingActive && !Thread.currentThread().isInterrupted) {
                    pollForScreenshots()
                }
            }
            Logger.debug("ScreenshotWatcherService", "poll_loop_exited")
        }
        t.name = "screenshot-poll-thread"
        t.isDaemon = true
        t.start()
        pollThread = t
    }

    private fun stopWatcher() {
        Logger.debug("ScreenshotWatcherService", "watcher_stop")
        pollingActive = false
        pollThread?.interrupt()
        pollThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Logger.debug("ScreenshotWatcherService", "watcher_stopped")
    }

    /**
     * Called by the OS (API 34+) when the `dataSync` foreground service hits its
     * ~6h runtime cap. START_STICKY does NOT restart a gracefully-stopped service,
     * so without this monitoring would die silently while config.enabled stays true.
     * Surface a visible notification, then stop ourselves.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTimeout(startId: Int) {
        Logger.warn("ScreenshotWatcherService", "fgs_timeout", mapOf("startId" to startId))
        postTimeoutNotification()
        pollingActive = false
        pollThread?.interrupt()
        pollThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun postTimeoutNotification() {
        val openAppPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("截屏监听已暂停 — 点击重新开启")
            .setContentIntent(openAppPi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTI_ID + 1, notif)
        Logger.debug("ScreenshotWatcherService", "timeout_notif_posted")
    }

    /**
     * Queries MediaStore for images added since the service started. Iterates all
     * new rows (not just moveToFirst) so screenshots taken between polls are not
     * missed. Each candidate is deduped by content URI and checked via isScreenshot.
     */
    private fun pollForScreenshots() {
        val projection = arrayOf(
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media._ID,
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(serviceStartedAtSec.toString())

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val relativePath = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH))
                val bucketName = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME))
                val dateAdded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                val mimeType = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE))
                val id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))

                val imageUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val imageUriStr = imageUri.toString()

                // Dedup by content URI first — skip rows we've already handled
                if (!seenUris.firstTimeFor(imageUriStr)) continue

                // Skip images that predate service start (belt-and-suspenders
                // in case the DATE_ADDED selection is slightly off on some ROMs)
                if (dateAdded < serviceStartedAtSec) {
                    Logger.debug("ScreenshotWatcherService", "image_too_old_skip",
                        mapOf("dateAdded" to dateAdded))
                    continue
                }

                val isShot = isScreenshot(relativePath, bucketName)
                val isCam = isCameraPhoto(relativePath, bucketName) && config.uploadCameraPhotos
                if (!isShot && !isCam) {
                    Logger.debug("ScreenshotWatcherService", "not_target_skip",
                        mapOf("relativePath" to relativePath))
                    continue
                }

                Logger.debug("ScreenshotWatcherService", "screenshot_detected", mapOf(
                    "uri" to imageUriStr,
                    "relativePath" to relativePath,
                    "mime" to mimeType,
                ))

                handleDetectedScreenshot(imageUriStr, mimeType)
            }
        }
    }

    /**
     * Branches on autoSend config:
     * - autoSend == true: upload immediately on a background thread; post a brief
     *   success notification on success, or the retry notification on failure.
     * - autoSend == false: post the confirm notification with [发到电脑] / [忽略].
     */
    private fun handleDetectedScreenshot(imageUriStr: String, mimeType: String) {
        if (config.autoSend) {
            Thread {
                uploadAndNotify(imageUriStr)
            }.also {
                it.name = "screenshot-upload-thread"
                it.isDaemon = true
                it.start()
            }
        } else {
            postScreenshotNotification(imageUriStr, mimeType)
        }
    }

    /**
     * Uploads the image at [uriStr] immediately.
     * On success, posts a brief auto-dismissing success notification.
     * On failure, posts the retry notification (same as the manual send path).
     */
    private fun uploadAndNotify(uriStr: String) {
        val notifId = NOTI_ID + 1 + (abs(uriStr.hashCode()) % 50_000)
        Logger.debug("ScreenshotWatcherService", "auto_upload_start", mapOf("uri" to uriStr))

        val ok = doUpload(uriStr)
        if (ok) {
            // 60s timeout (not 5s): when shooting with the camera, the camera
            // app is full-screen and hides the notification — a 5s notification
            // is gone before the user exits the camera. 60s survives that.
            val successNotif = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("✅ 已发到电脑")
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .build()
            getSystemService(NotificationManager::class.java).notify(SUCCESS_NOTI_ID, successNotif)
            Logger.debug("ScreenshotWatcherService", "auto_upload_success", mapOf("uri" to uriStr))
        } else {
            Logger.warn("ScreenshotWatcherService", "auto_upload_failed", mapOf("uri" to uriStr))
            postRetryNotification(uriStr, notifId)
        }
    }

    /**
     * Core upload logic shared by the auto-send path and the manual ACTION_SEND path.
     * Returns true on success.
     */
    private fun doUpload(uriStr: String): Boolean {
        val uri = Uri.parse(uriStr)
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Logger.warn("ScreenshotWatcherService", "read_bytes_fail",
                mapOf("reason" to e.message), e)
            null
        }

        if (bytes == null) {
            Logger.warn("ScreenshotWatcherService", "upload_abort_no_bytes",
                mapOf("uri" to uriStr))
            return false
        }

        val mime = contentResolver.getType(uri) ?: "image/png"
        val takenAtMs = System.currentTimeMillis()

        return uploader.upload(
            baseUrl = config.uploadUrl,
            imageBytes = bytes,
            mimeType = mime,
            source = config.deviceName,
            analyze = false,
            takenAtMs = takenAtMs,
        )
    }

    private fun postRetryNotification(uriStr: String, notifId: Int) {
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
        val failNotif = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("发送失败，点击重试")
            .setContentIntent(retryPi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(notifId, failNotif)
    }

    private fun postScreenshotNotification(uriStr: String, mimeType: String) {
        // Keep notifId in [NOTI_ID+1, NOTI_ID+50000] — never overflows Int, never
        // collides with the persistent foreground NOTI_ID.
        val notifId = NOTI_ID + 1 + (abs(uriStr.hashCode()) % 50_000)

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
        // Distinct requestCode from sendPi — offset by 50000 to stay clear of any
        // other per-screenshot notifId in the [NOTI_ID+1, NOTI_ID+50000] range.
        val dismissPi = PendingIntent.getService(
            this, notifId + 50_000,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ALERTS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("检测到截图")
            .setAutoCancel(true)
            .addAction(0, "发到电脑", sendPi)
            .addAction(0, "忽略", dismissPi)
            .build()

        getSystemService(NotificationManager::class.java).notify(notifId, notif)
        Logger.debug("ScreenshotWatcherService", "screenshot_notif_posted",
            mapOf("notifId" to notifId, "uri" to uriStr))
    }

    private fun handleSend(uriStr: String, notifId: Int) {
        Logger.debug("ScreenshotWatcherService", "upload_start", mapOf("uri" to uriStr))
        val ok = doUpload(uriStr)
        if (ok) {
            getSystemService(NotificationManager::class.java).cancel(notifId)
            Logger.debug("ScreenshotWatcherService", "upload_success_notif_cancelled",
                mapOf("notifId" to notifId))
        } else {
            Logger.warn("ScreenshotWatcherService", "upload_failed",
                mapOf("notifId" to notifId, "uri" to uriStr))
            postRetryNotification(uriStr, notifId)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            // Persistent foreground notification — silent.
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "截屏监听", NotificationManager.IMPORTANCE_LOW)
            )
            // Per-screenshot alerts — DEFAULT so they make a sound / heads-up and
            // the user actually notices them.
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS_ID, "截屏提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
            Logger.debug("ScreenshotWatcherService", "notif_channels_created",
                mapOf("ids" to "$CHANNEL_ID,$CHANNEL_ALERTS_ID"))
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
        pollingActive = false
        pollThread?.interrupt()
        pollThread = null
        super.onDestroy()
    }
}
