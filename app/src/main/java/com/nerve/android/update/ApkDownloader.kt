package com.nerve.android.update

import com.nerve.android.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

open class ApkDownloader(
    private val client: OkHttpClient = defaultClient(),
) {
    open suspend fun download(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        runCatching {
            dest.parentFile?.mkdirs()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("empty body")
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                Logger.debug(
                    "ApkDownloader",
                    "download_begin",
                    mapOf("url" to url, "total" to total, "dest" to dest.path),
                )
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            downloaded += n
                            onProgress(downloaded, total)
                        }
                        output.flush()
                        Logger.debug(
                            "ApkDownloader",
                            "download_done",
                            mapOf("bytes" to downloaded),
                        )
                    }
                }
                dest
            }
        }.onFailure { error ->
            Logger.warn(
                "ApkDownloader",
                "download_fail",
                mapOf("url" to url, "reason" to error.message),
            )
            dest.delete()
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
