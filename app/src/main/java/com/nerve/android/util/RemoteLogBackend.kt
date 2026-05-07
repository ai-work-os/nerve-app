package com.nerve.android.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Ships WARN+ERROR log lines to a remote collector in batches.
 *
 * The buffer is a fixed-size deque: when it fills up the oldest line drops
 * so newer events always survive. Failed POSTs keep the batch in the
 * buffer for the next flush, so a brief outage doesn't lose anything.
 */
class RemoteLogBackend(
    private val endpointUrl: String,
    private val deviceIdProvider: () -> String,
    private val versionCodeProvider: () -> Int,
    scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
    private val flushIntervalMs: Long = 30_000L,
    private val flushBatchSize: Int = 50,
    private val bufferCap: Int = 1000,
) : LogBackend {
    @Serializable
    private data class Payload(
        val deviceId: String,
        val versionCode: Int,
        val lines: List<String>,
    )

    private val buffer = ArrayDeque<String>()
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }

    init {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(flushIntervalMs)
                runCatching { flushNow() }
            }
        }
    }

    override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
        if (level == LogLevel.DEBUG) return
        val prefix = when (level) {
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
            LogLevel.DEBUG -> return
        }
        val rendered = buildString {
            append(prefix).append('/').append(tag).append(' ').append(line)
            if (throwable != null) {
                append(' ').append(throwable.javaClass.simpleName)
                throwable.message?.let { append(": ").append(it) }
            }
        }
        addToBuffer(rendered)
    }

    suspend fun flushNow() {
        val snapshot = mutex.withLock { buffer.toList() }
        if (snapshot.isEmpty()) return

        val deviceId = runCatching { deviceIdProvider() }.getOrDefault("?")
        val versionCode = runCatching { versionCodeProvider() }.getOrDefault(-1)
        val body = json.encodeToString(
            Payload(deviceId = deviceId, versionCode = versionCode, lines = snapshot),
        )
        val request = Request.Builder()
            .url(endpointUrl)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val ok = runCatching {
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)

        if (ok) {
            mutex.withLock {
                // Remove only what we sent — new lines may have been added meanwhile
                repeat(snapshot.size) { buffer.pollFirst() }
            }
        }
        // On failure: keep buffer untouched, retry next flush
    }

    private fun addToBuffer(line: String) {
        synchronized(buffer) {
            while (buffer.size >= bufferCap) {
                buffer.pollFirst()
            }
            buffer.offerLast(line)
        }
    }

    private fun currentSize(): Int = synchronized(buffer) { buffer.size }
}
