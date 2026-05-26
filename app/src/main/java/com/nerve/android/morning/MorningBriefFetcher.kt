package com.nerve.android.morning

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MorningBriefFetcher(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    fun fetch(): MorningBrief {
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/morning-brief/today")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("empty body")
            return json.decodeFromString(MorningBrief.serializer(), body)
        }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
