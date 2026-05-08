package com.nerve.android.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteLogBackendTest {
    private val server = MockWebServer()
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        server.start()
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    private fun makeBackend(
        scope: TestScope,
        flushIntervalMs: Long = 30_000L,
        flushBatchSize: Int = 50,
        bufferCap: Int = 1000,
    ): RemoteLogBackend = RemoteLogBackend(
        endpointUrl = server.url("/log").toString(),
        deviceIdProvider = { "test-device" },
        versionCodeProvider = { 99 },
        scope = scope.backgroundScope,
        client = OkHttpClient(),
        flushIntervalMs = flushIntervalMs,
        flushBatchSize = flushBatchSize,
        bufferCap = bufferCap,
    )

    @Test
    fun `DEBUG entries are not sent`() = runTest(dispatcher) {
        val backend = makeBackend(this, flushBatchSize = 1)
        backend.write(LogLevel.DEBUG, "Tag", "should be ignored")
        backend.flushNow()
        advanceUntilIdle()
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `WARN entries are batched and posted`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val backend = makeBackend(this, flushBatchSize = 1)
        backend.write(LogLevel.WARN, "NerveClient", "event=connect_fail reason=x")
        backend.flushNow()
        advanceUntilIdle()
        assertEquals(1, server.requestCount)
        val body = server.takeRequest(1, TimeUnit.SECONDS)!!.bodyAsJson()
        assertEquals("test-device", body.jsonObject["deviceId"]!!.jsonPrimitive.content)
        assertEquals("99", body.jsonObject["versionCode"]!!.jsonPrimitive.content)
        val lines = body.jsonObject["lines"]!!.jsonArray
        assertEquals(1, lines.size)
        assertEquals("W/NerveClient event=connect_fail reason=x", lines[0].jsonPrimitive.content)
    }

    @Test
    fun `ERROR entries are sent`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200))
        val backend = makeBackend(this, flushBatchSize = 1)
        backend.write(LogLevel.ERROR, "X", "boom")
        backend.flushNow()
        advanceUntilIdle()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `failed POST keeps lines for next flush`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(200))
        val backend = makeBackend(this, flushBatchSize = 1)
        backend.write(LogLevel.WARN, "X", "first")
        backend.flushNow()
        advanceUntilIdle()
        // first POST 5xx; the line should still be re-attempted next flush
        backend.flushNow()
        advanceUntilIdle()
        assertEquals(2, server.requestCount)
        // second request should still contain "first" (no new lines added)
        val first = server.takeRequest(1, TimeUnit.SECONDS)!!.bodyAsJson()
        val second = server.takeRequest(1, TimeUnit.SECONDS)!!.bodyAsJson()
        assertEquals(first.jsonObject["lines"]!!.jsonArray, second.jsonObject["lines"]!!.jsonArray)
    }

    @Test
    fun `bufferCap drops oldest when overflowing`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200))
        val backend = makeBackend(this, flushBatchSize = 100, bufferCap = 3)
        backend.write(LogLevel.WARN, "X", "1")
        backend.write(LogLevel.WARN, "X", "2")
        backend.write(LogLevel.WARN, "X", "3")
        backend.write(LogLevel.WARN, "X", "4") // pushes out "1"
        backend.flushNow()
        advanceUntilIdle()
        val body = server.takeRequest(1, TimeUnit.SECONDS)!!.bodyAsJson()
        val lines = body.jsonObject["lines"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("W/X 2", "W/X 3", "W/X 4"), lines)
    }
}

private fun RecordedRequest.bodyAsJson() =
    Json.parseToJsonElement(body.readUtf8())
