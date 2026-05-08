package com.nerve.android.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ApkDownloaderTest {
    private val server = MockWebServer()
    private lateinit var workDir: File

    @BeforeEach
    fun setUp() {
        server.start()
        workDir = File.createTempFile("apk-download-", "").apply {
            delete()
            mkdirs()
        }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        workDir.deleteRecursively()
    }

    @Test
    fun `downloads body to dest and reports progress`() = runTest {
        val payload = ByteArray(8192) { it.toByte() }
        val body = Buffer().apply { write(payload) }
        server.enqueue(MockResponse().setBody(body).setHeader("Content-Length", payload.size.toString()))

        val dest = File(workDir, "out.apk")
        val progress = mutableListOf<Pair<Long, Long>>()
        val downloader = ApkDownloader(client = OkHttpClient())

        val result = downloader.download(
            url = server.url("/apk").toString(),
            dest = dest,
            onProgress = { downloaded, total -> progress += downloaded to total },
        )

        assertTrue(result.isSuccess, "result=$result")
        assertEquals(payload.size.toLong(), dest.length())
        assertTrue(progress.isNotEmpty())
        assertEquals(payload.size.toLong(), progress.last().first)
        assertEquals(payload.size.toLong(), progress.last().second)
    }

    @Test
    fun `returns failure on non 2xx`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val dest = File(workDir, "out.apk")
        val result = ApkDownloader(client = OkHttpClient()).download(
            url = server.url("/missing").toString(),
            dest = dest,
            onProgress = { _, _ -> },
        )
        assertTrue(result.isFailure)
        assertFalse(dest.exists() && dest.length() > 0L, "should not leave a partial file")
    }
}
