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
            logFlush(manual, "flush_skip", mapOf("trigger" to trigger, "reason" to "in_flight"))
            return 0
        }
        return try {
            logFlush(manual, "flush_start", mapOf("trigger" to trigger))
            val sent = flush()
            logFlush(manual, "flush_done", mapOf("sent" to sent, "trigger" to trigger))
            sent
        } finally {
            flushInFlight.set(false)
        }
    }

    private fun logFlush(manual: Boolean, event: String, fields: Map<String, Any>) {
        if (manual) {
            Logger.warn("SyncPolicy", event, fields)
        } else {
            Logger.debug("SyncPolicy", event, fields)
        }
    }
}
