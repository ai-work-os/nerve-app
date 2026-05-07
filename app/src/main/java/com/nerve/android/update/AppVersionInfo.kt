package com.nerve.android.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppVersionInfo(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
    @SerialName("url") val url: String,
    @SerialName("notes") val notes: String? = null,
) {
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(payload: String): AppVersionInfo? = runCatching {
            json.decodeFromString(serializer(), payload)
        }.getOrNull()
    }
}
