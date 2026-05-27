package com.nerve.android.lifelog

import com.nerve.android.util.Logger
import java.util.concurrent.atomic.AtomicBoolean

class SyncPolicy(
  private val flush: () -> Int,                    // returns chunks sent
  private val isUnmeteredNow: () -> Boolean,
) {
  private val flushInFlight = AtomicBoolean(false)

  /** Called periodically (e.g. every 30s) and on network change events. */
  fun tickAuto() {
    if (!isUnmeteredNow()) {
      Logger.debug("SyncPolicy", "auto_skip", mapOf("reason" to "metered"))
      return
    }
    flushGuarded(trigger = "auto", manual = false)
  }

  /** User pressed "立即上传" button. Ignores network policy. */
  fun flushNow(): Int {
    return flushGuarded(trigger = "manual", manual = true)
  }

  private fun flushGuarded(trigger: String, manual: Boolean): Int {
    if (!flushInFlight.compareAndSet(false, true)) {
      val fields = mapOf("trigger" to trigger, "reason" to "in_flight")
      if (manual) {
        Logger.warn("SyncPolicy", "flush_skip", fields)
      } else {
        Logger.debug("SyncPolicy", "flush_skip", fields)
      }
      return 0
    }
    return try {
      if (manual) {
        Logger.warn("SyncPolicy", "flush_start", mapOf("trigger" to trigger))
      } else {
        Logger.debug("SyncPolicy", "flush_start", mapOf("trigger" to trigger))
      }
      val sent = flush()
      val fields = mapOf("sent" to sent, "trigger" to trigger)
      if (manual) {
        Logger.warn("SyncPolicy", "flush_done", fields)
      } else {
        Logger.debug("SyncPolicy", "flush_done", fields)
      }
      sent
    } finally {
      flushInFlight.set(false)
    }
  }
}
