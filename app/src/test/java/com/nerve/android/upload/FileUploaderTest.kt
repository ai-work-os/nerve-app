package com.nerve.android.upload

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class FileUploaderTest {
    private lateinit var server: MockWebServer
    private lateinit var uploader: FileUploader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        uploader = FileUploader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `uploads raw file bytes and parses returned path metadata`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "path": "/home/renjinxi/.nerve/uploads/2026-06-04/abc-notes.md",
                      "name": "notes.md",
                      "mimeType": "text/markdown",
                      "sizeBytes": 12,
                      "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    }
                    """.trimIndent(),
                ),
        )

        val result = uploader.upload(
            baseUrl = server.url("/").toString().trimEnd('/'),
            file = PendingFileUpload(
                name = "notes.md",
                mimeType = "text/markdown",
                bytes = "# hi\n".toByteArray(),
            ),
        )

        assertEquals("/home/renjinxi/.nerve/uploads/2026-06-04/abc-notes.md", result.path)
        assertEquals("notes.md", result.name)
        assertEquals("text/markdown", result.mimeType)
        assertEquals(12, result.sizeBytes)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result.sha256)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/files/upload", req.path)
        assertEquals("text/markdown", req.getHeader("Content-Type"))
        assertEquals("notes.md", req.getHeader("X-File-Name-Encoded"))
        assertEquals("# hi\n", req.body.readUtf8())
    }

    @Test
    fun `throws readable error for upload limit response`() {
        server.enqueue(MockResponse().setResponseCode(413).setBody("""{"error":"file too large"}"""))

        val error = assertFailsWith<FileUploadException> {
            uploader.upload(
                baseUrl = server.url("/").toString().trimEnd('/'),
                file = PendingFileUpload("big.zip", "application/zip", ByteArray(5)),
            )
        }

        assertEquals("file too large", error.message)
    }

    @Test
    fun `url encodes unicode filename header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "path": "/home/renjinxi/.nerve/uploads/2026-06-04/abc.md",
                      "name": "需求说明.md",
                      "mimeType": "text/markdown",
                      "sizeBytes": 5,
                      "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                    }
                    """.trimIndent(),
                ),
        )

        uploader.upload(
            baseUrl = server.url("/").toString().trimEnd('/'),
            file = PendingFileUpload("需求说明.md", "text/markdown", "# hi\n".toByteArray()),
        )

        val req = server.takeRequest()
        assertEquals("%E9%9C%80%E6%B1%82%E8%AF%B4%E6%98%8E.md", req.getHeader("X-File-Name-Encoded"))
    }
}
