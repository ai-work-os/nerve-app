package com.nerve.android.lifelog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UploadQueueTest {
  private lateinit var ctx: Context
  private lateinit var q: UploadQueue

  @Before fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    q = UploadQueue(ctx)
    q.clear()
  }

  @After fun tearDown() { q.close() }

  private fun chunk(id: String, ts: Long = 1700000000000L) =
    Chunk(id, "dev1", ts, 60000, "/tmp/$id.opus")

  @Test fun `enqueue and pop pending in FIFO order`() {
    q.enqueue(chunk("a", 1L))
    q.enqueue(chunk("b", 2L))
    val first = q.nextPending(); assertEquals("a", first?.chunkId)
    q.markDone("a")
    val second = q.nextPending(); assertEquals("b", second?.chunkId)
  }

  @Test fun `markFailed increments attempts`() {
    q.enqueue(chunk("x"))
    q.markFailed("x", "network error")
    val item = q.nextPending(includeFailed = true)
    assertNotNull(item)
    assertEquals(1, q.getAttempts("x"))
    q.markFailed("x", "still failing")
    assertEquals(2, q.getAttempts("x"))
  }

  @Test fun `markDone removes from queue`() {
    q.enqueue(chunk("z"))
    q.markDone("z")
    assertNull(q.nextPending())
    assertEquals(0, q.pendingCount())
  }

  @Test fun `survives reopen (persistence)`() {
    q.enqueue(chunk("p"))
    q.close()
    val q2 = UploadQueue(ctx)
    assertEquals(1, q2.pendingCount())
    assertEquals("p", q2.nextPending()?.chunkId)
    q2.close()
  }
}
