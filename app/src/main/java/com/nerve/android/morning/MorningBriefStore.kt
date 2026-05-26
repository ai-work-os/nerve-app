package com.nerve.android.morning

import android.content.Context
import kotlinx.serialization.json.Json

class MorningBriefStore(context: Context) {
    private val prefs = context.getSharedPreferences("morning-brief", Context.MODE_PRIVATE)

    fun save(brief: MorningBrief) {
        prefs.edit()
            .putString(KEY_BRIEF, json.encodeToString(MorningBrief.serializer(), brief))
            .remove(KEY_ERROR)
            .apply()
    }

    fun saveError(message: String) {
        prefs.edit().putString(KEY_ERROR, message).apply()
    }

    fun load(): MorningBrief? {
        val raw = prefs.getString(KEY_BRIEF, null) ?: return null
        return runCatching { json.decodeFromString(MorningBrief.serializer(), raw) }.getOrNull()
    }

    fun lastError(): String? = prefs.getString(KEY_ERROR, null)

    companion object {
        private const val KEY_BRIEF = "brief"
        private const val KEY_ERROR = "error"
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
