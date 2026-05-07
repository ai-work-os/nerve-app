package com.nerve.android.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class HttpVersionFetcher(
    private val versionUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
) : suspend () -> String {
    override suspend fun invoke(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(versionUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string() ?: error("empty body")
        }
    }
}
