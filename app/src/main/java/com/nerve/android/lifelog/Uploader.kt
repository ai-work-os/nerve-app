package com.nerve.android.lifelog

import com.nerve.android.util.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class Uploader(
  private val client: OkHttpClient,
  private val queue: UploadQueue,
  private val homeUrl: String,
  private val token: String,
) {
  /** Returns true on 2xx success. */
  fun uploadOne(c: Chunk): Boolean {
    val file = File(c.path)
    if (!file.exists()) {
      // Source file gone — drop from queue
      Logger.warn("Uploader", "file_missing", mapOf("chunkId" to c.chunkId, "path" to c.path))
      queue.markDone(c.chunkId)
      return true
    }
    Logger.debug("Uploader", "upload_attempt", mapOf(
      "chunkId" to c.chunkId,
      "sizeBytes" to file.length(),
      "homeUrl" to homeUrl,
    ))
    queue.markUploading(c.chunkId)
    val meta = JSONObject().apply {
      put("deviceId", c.deviceId)
      put("recordedAtMs", c.recordedAtMs)
      put("durationMs", c.durationMs)
      put("chunkId", c.chunkId)
    }.toString()
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
      .addFormDataPart("meta", meta)
      .addFormDataPart("file", "${c.chunkId}.opus",
        file.asRequestBody("audio/ogg".toMediaType()))
      .build()
    val req = Request.Builder()
      .url("$homeUrl/plugins/ai-life-log/upload")
      .header("X-LifeLog-Token", token)
      .header("X-Device-Id", c.deviceId)
      .header("X-Recorded-At-Ms", c.recordedAtMs.toString())
      .header("X-Chunk-Id", c.chunkId)
      .post(body)
      .build()
    return try {
      client.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
          Logger.debug("Uploader", "upload_success", mapOf("chunkId" to c.chunkId, "code" to resp.code))
          queue.markDone(c.chunkId)
          file.delete()
          true
        } else {
          Logger.warn("Uploader", "upload_fail", mapOf(
            "chunkId" to c.chunkId,
            "code" to resp.code,
            "attempts" to queue.getAttempts(c.chunkId) + 1,
          ))
          queue.markFailed(c.chunkId, "http ${resp.code}")
          false
        }
      }
    } catch (e: IOException) {
      Logger.warn("Uploader", "upload_io_fail", mapOf("chunkId" to c.chunkId, "reason" to e.message), e)
      queue.markFailed(c.chunkId, e.message ?: "io error")
      false
    }
  }

  /** Drain pending queue until empty or one upload fails (caller decides retry timing). */
  fun flushAll(): Int {
    var sent = 0
    while (true) {
      val c = queue.nextPending() ?: break
      if (!uploadOne(c)) break
      sent++
    }
    Logger.debug("Uploader", "flush_done", mapOf("sent" to sent))
    return sent
  }
}
