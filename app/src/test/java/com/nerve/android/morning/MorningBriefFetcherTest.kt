package com.nerve.android.morning

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MorningBriefFetcherTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetches morning brief from server endpoint`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "date":"2026-05-26",
                      "sourceDate":"2026-05-25",
                      "generatedAt":"2026-05-26T00:30:00.000Z",
                      "notificationTitle":"早报已准备好",
                      "notificationBody":"昨天 ERP 有进展，今天优先收口。",
                      "markdown":"# 早报\n\n## 今天建议优先做什么\n- 收口 ERP",
                      "sections":[{"title":"今天建议优先做什么","items":["收口 ERP"]}],
                      "sources":[{"kind":"observer","path":"/tmp/events.jsonl","available":true}]
                    }
                    """.trimIndent(),
                ),
        )

        val brief = MorningBriefFetcher(
            baseUrl = server.url("/").toString().trimEnd('/'),
            client = OkHttpClient(),
        ).fetch()

        assertEquals("2026-05-26", brief.date)
        assertEquals("早报已准备好", brief.notificationTitle)
        assertTrue(brief.markdown.contains("收口 ERP"))
        assertEquals("/morning-brief/today", server.takeRequest().path)
    }
}
