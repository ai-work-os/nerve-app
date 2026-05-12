package com.nerve.android.lifelog

import com.nerve.android.util.Logger

class SyncPolicy(
  private val flush: () -> Int,                    // returns chunks sent
  private val isUnmeteredNow: () -> Boolean,
) {
  /** Called periodically (e.g. every 30s) and on network change events. */
  fun tickAuto() {
    if (!isUnmeteredNow()) {
      Logger.debug("SyncPolicy", "auto_skip", mapOf("reason" to "metered"))
      return
    }
    Logger.debug("SyncPolicy", "flush_start", mapOf("trigger" to "auto"))
    val sent = flush()
    Logger.debug("SyncPolicy", "flush_done", mapOf("sent" to sent, "trigger" to "auto"))
  }

  /** User pressed "立即上传" button. Ignores network policy. */
  fun flushNow(): Int {
    Logger.warn("SyncPolicy", "flush_start", mapOf("trigger" to "manual"))
    val sent = flush()
    Logger.debug("SyncPolicy", "flush_done", mapOf("sent" to sent, "trigger" to "manual"))
    return sent
  }
}
