package com.nerve.android.lifelog

import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UploaderTest {
  private lateinit var ctx: Context
  private lateinit var server: MockWebServer
  private lateinit var queue: UploadQueue
  private lateinit var uploader: Uploader

  @Before fun setUp() {
    ctx = ApplicationProvider.getApplicationContext()
    server = MockWebServer().apply { start() }
    queue = UploadQueue(ctx).apply { clear() }
    uploader = Uploader(
      client = OkHttpClient(),
      queue = queue,
      homeUrl = server.url("/").toString().trimEnd('/'),
      token = "secret",
    )
  }

  @After fun tearDown() {
    queue.close(); server.shutdown()
  }

  private fun makeChunk(id: String): Chunk {
    val f = File(ctx.cacheDir, "$id.opus").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    return Chunk(id, "dev1", 1700000000000L, 60000, f.absolutePath)
  }

  @Test fun `success removes chunk from queue and deletes file`() {
    val c = makeChunk("a"); queue.enqueue(c)
    server.enqueue(MockResponse().setResponseCode(200)
      .setBody("""{"ok":true,"chunkId":"a"}"""))
    val ok = uploader.uploadOne(c)
    assertTrue(ok)
    assertEquals(0, queue.pendingCount())
    assertTrue(!File(c.path).exists())
    val req = server.takeRequest()
    assertEquals("POST", req.method)
    assertTrue(req.path?.endsWith("/plugins/ai-life-log/upload") == true)
    assertEquals("secret", req.getHeader("X-LifeLog-Token"))
    assertEquals("dev1", req.getHeader("X-Device-Id"))
    val body = req.body.readUtf8()
    assertTrue(body.contains("\"deviceId\":\"dev1\""))
    assertTrue(body.contains("\"chunkId\":\"a\""))
  }

  @Test fun `5xx marks failed and increments attempts`() {
    val c = makeChunk("b"); queue.enqueue(c)
    server.enqueue(MockResponse().setResponseCode(503))
    val ok = uploader.uploadOne(c)
    assertTrue(!ok)
    assertEquals(1, queue.getAttempts("b"))
  }

  @Test fun `401 marks failed (auth)`() {
    val c = makeChunk("c"); queue.enqueue(c)
    server.enqueue(MockResponse().setResponseCode(401))
    val ok = uploader.uploadOne(c)
    assertTrue(!ok)
    assertEquals(1, queue.getAttempts("c"))
  }
}
