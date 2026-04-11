package com.nerve.android.transport

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealNerveClientOkHttpConfigTest {
    @Test
    fun `default requestTimeoutMs is at least 120s for prompt which waits for AI completion`() {
        val client = RealNerveClient()
        assertTrue(client.configuredRequestTimeoutMs >= 120_000,
            "requestTimeoutMs should be >= 120s for AI prompt responses, but was ${client.configuredRequestTimeoutMs}ms")
    }

    @Test
    fun `default OkHttpClient has readTimeout 0 for long-lived WebSocket`() {
        val client = RealNerveClient()
        assertEquals(0, client.httpClient.readTimeoutMillis)
    }

    @Test
    fun `default OkHttpClient has pingInterval 15 seconds`() {
        val client = RealNerveClient()
        assertEquals(15_000, client.httpClient.pingIntervalMillis)
    }
}
