package com.nerve.android.lifelog

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class LifeLogConfig(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences("lifelog_config", Context.MODE_PRIVATE)

  var homeUrl: String
    get() = prefs.getString("home_url", "") ?: ""
    set(v) { prefs.edit().putString("home_url", v).apply() }

  var token: String
    get() = prefs.getString("token", "") ?: ""
    set(v) { prefs.edit().putString("token", v).apply() }

  /** Stable random UUID generated on first access; persists across reinstalls only if backups enabled. */
  val deviceId: String
    get() {
      val existing = prefs.getString("device_id", null)
      if (existing != null) return existing
      val fresh = UUID.randomUUID().toString().take(8)
      prefs.edit().putString("device_id", fresh).apply()
      return fresh
    }
}
