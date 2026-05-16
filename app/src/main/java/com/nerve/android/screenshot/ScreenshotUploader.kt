package com.nerve.android.screenshot

import com.nerve.android.util.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Uploads a screenshot's raw bytes to the `screenshot` plugin's /screenshot/upload endpoint. */
class ScreenshotUploader(private val client: OkHttpClient) {

    /** Returns true on a 2xx response. */
    fun upload(
        baseUrl: String,
        imageBytes: ByteArray,
        mimeType: String,
        source: String,
        analyze: Boolean,
        takenAtMs: Long,
    ): Boolean {
        val body = imageBytes.toRequestBody(mimeType.toMediaType())
        val req = Request.Builder()
            .url("$baseUrl/screenshot/upload")
            .header("X-Source", source)
            .header("X-Analyze", analyze.toString())
            .header("X-Taken-At", takenAtMs.toString())
            .post(body)
            .build()
        Logger.debug("ScreenshotUploader", "upload_attempt", mapOf(
            "baseUrl" to baseUrl, "bytes" to imageBytes.size, "mime" to mimeType, "analyze" to analyze,
        ))
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Logger.debug("ScreenshotUploader", "upload_success", mapOf("code" to resp.code))
                    true
                } else {
                    Logger.warn("ScreenshotUploader", "upload_fail", mapOf("code" to resp.code))
                    false
                }
            }
        } catch (e: IOException) {
            Logger.warn("ScreenshotUploader", "upload_io_fail", mapOf("reason" to e.message), e)
            false
        }
    }
}
