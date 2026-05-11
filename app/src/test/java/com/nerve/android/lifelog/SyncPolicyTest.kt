package com.nerve.android.lifelog

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
  @Test fun `auto mode skips upload on metered network`() {
    var flushCalls = 0
    val policy = SyncPolicy(
      flush = { flushCalls++; 0 },
      isUnmeteredNow = { false },
    )
    policy.tickAuto()
    assertEquals(0, flushCalls)
  }

  @Test fun `auto mode flushes on unmetered network`() {
    var flushCalls = 0
    val policy = SyncPolicy(
      flush = { flushCalls++; 1 },
      isUnmeteredNow = { true },
    )
    policy.tickAuto()
    assertEquals(1, flushCalls)
  }

  @Test fun `manual ignores network and flushes`() {
    var flushCalls = 0
    val policy = SyncPolicy(
      flush = { flushCalls++; 0 },
      isUnmeteredNow = { false },
    )
    policy.flushNow()
    assertEquals(1, flushCalls)
  }
}
