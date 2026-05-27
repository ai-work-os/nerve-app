package com.nerve.android.morning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningBriefPresentationTest {
    @Test
    fun `presentation keeps summary short and exposes friendly network error`() {
        val brief = MorningBrief(
            date = "2026-05-26",
            sourceDate = "2026-05-25",
            generatedAt = "2026-05-26T00:30:00.000Z",
            notificationTitle = "早报已准备好",
            notificationBody = "23:35 **conversation-archive** — ok — home: 34 条 — /home/renjinxi/.ai/workspace/activity/conversations/other/2026-05-25-home.md；打开 Nerve 看完整早报后，只挑 1-3 件今天必须完成的事推进。",
            markdown = "# 早报",
            sections = listOf(
                MorningBriefSection(
                    title = "昨天我做了什么",
                    items = listOf("23:35 **conversation-archive** — ok — home: 34 条 — /home/renjinxi/.ai/workspace/activity/conversations/other/2026-05-25-home.md"),
                ),
            ),
            sources = emptyList(),
        )

        val model = MorningBriefPresentation.from(
            brief = brief,
            loading = false,
            error = "failed to connect to /100.75.43.90 (port 4800) from /10.140.140.71 (port 49078) after 10000ms",
        )

        assertEquals("5月26日早报", model.title)
        assertTrue(model.summary.length <= 90)
        assertFalse(model.summary.contains("/home/renjinxi"))
        assertFalse(model.sections.first().items.first().contains("conversation-archive"))
        assertEquals("暂时连不上服务器，稍后会自动重试。", model.statusMessage)
    }
}
