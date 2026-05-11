package com.nerve.android.lifelog

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ChunkWriterTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test fun `writes opus to chunks dir with stable id`() {
    val writer = ChunkWriter(context, deviceId = "dev1")
    val data = "fake-opus-payload".toByteArray()
    val chunk = writer.write(data, recordedAtMs = 1700000000000L, durationMs = 60000)
    assertTrue(chunk.chunkId.length == 16)
    assertEquals("dev1", chunk.deviceId)
    assertEquals(1700000000000L, chunk.recordedAtMs)
    assertEquals(60000L, chunk.durationMs)
    val file = File(chunk.path)
    assertTrue(file.exists())
    assertEquals(data.size, file.length().toInt())
  }

  @Test fun `same deviceId+recordedAt produces same chunkId (idempotent)`() {
    val writer = ChunkWriter(context, deviceId = "dev1")
    val a = writer.write(byteArrayOf(1), 1700000000000L, 60000)
    val b = writer.write(byteArrayOf(2), 1700000000000L, 60000)
    assertEquals(a.chunkId, b.chunkId)
  }
}
