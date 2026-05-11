package com.nerve.android.lifelog

class SyncPolicy(
  private val flush: () -> Int,                    // returns chunks sent
  private val isUnmeteredNow: () -> Boolean,
) {
  /** Called periodically (e.g. every 30s) and on network change events. */
  fun tickAuto() {
    if (isUnmeteredNow()) flush()
  }

  /** User pressed "立即上传" button. Ignores network policy. */
  fun flushNow(): Int = flush()
}
