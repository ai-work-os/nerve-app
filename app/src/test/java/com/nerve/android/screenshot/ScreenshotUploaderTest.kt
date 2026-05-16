package com.nerve.android.screenshot

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreenshotUploaderTest {
    private lateinit var server: MockWebServer
    private lateinit var uploader: ScreenshotUploader

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        uploader = ScreenshotUploader(OkHttpClient())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `成功上传 raw 图片 + 正确的 header`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"blobId":"abc"}"""))
        val ok = uploader.upload(
            baseUrl = server.url("/").toString().trimEnd('/'),
            imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            mimeType = "image/png",
            source = "Pixel-8",
            analyze = false,
            takenAtMs = 1747000000000L,
        )
        assertTrue(ok)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/screenshot/upload", req.path)
        assertEquals("image/png", req.getHeader("Content-Type"))
        assertEquals("Pixel-8", req.getHeader("X-Source"))
        assertEquals("false", req.getHeader("X-Analyze"))
        assertEquals("1747000000000", req.getHeader("X-Taken-At"))
        assertEquals(4, req.body.size)
    }

    @Test fun `analyze=true 时 header 为 true`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        uploader.upload(server.url("/").toString().trimEnd('/'), byteArrayOf(1), "image/jpeg", "d", true, 1L)
        assertEquals("true", server.takeRequest().getHeader("X-Analyze"))
    }

    @Test fun `非 2xx 返回 false`() {
        server.enqueue(MockResponse().setResponseCode(413))
        val ok = uploader.upload(server.url("/").toString().trimEnd('/'), byteArrayOf(1), "image/png", "d", false, 1L)
        assertFalse(ok)
    }
}
