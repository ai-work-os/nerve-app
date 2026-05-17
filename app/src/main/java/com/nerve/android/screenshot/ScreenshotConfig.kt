package com.nerve.android.screenshot

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

class ScreenshotConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("screenshot_config", Context.MODE_PRIVATE)

    /** Base URL of the screenshot plugin HTTP server, e.g. http://100.75.43.90:4812 */
    var uploadUrl: String
        get() = prefs.getString("upload_url", DEFAULT_UPLOAD_URL) ?: DEFAULT_UPLOAD_URL
        set(v) { prefs.edit().putString("upload_url", v).apply() }

    /** Whether the watcher service should run. */
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }

    /** Human-readable device name sent as X-Source. */
    val deviceName: String
        get() = (Build.MODEL ?: "android").replace(Regex("[^A-Za-z0-9_-]"), "-")

    companion object {
        const val DEFAULT_UPLOAD_URL = "http://100.75.43.90:4812"
    }
}
