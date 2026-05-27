package com.nerve.android.lifelog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

  @Test fun `manual flush skips while another flush is running`() {
    val enteredFlush = CountDownLatch(1)
    val releaseFlush = CountDownLatch(1)
    val flushCalls = AtomicInteger(0)
    val policy = SyncPolicy(
      flush = {
        flushCalls.incrementAndGet()
        enteredFlush.countDown()
        releaseFlush.await(2, TimeUnit.SECONDS)
        1
      },
      isUnmeteredNow = { false },
    )

    val first = Thread { policy.flushNow() }.also { it.start() }
    enteredFlush.await(2, TimeUnit.SECONDS)

    val skipped = policy.flushNow()
    releaseFlush.countDown()
    first.join(2_000)

    assertEquals(0, skipped)
    assertEquals(1, flushCalls.get())
  }
}
