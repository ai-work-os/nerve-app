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

    @Test
    fun `presentation merges duplicate sections and exposes source diagnostics`() {
        val brief = MorningBrief(
            date = "2026-06-04",
            sourceDate = "2026-06-03",
            generatedAt = "2026-06-04T00:30:00.000Z",
            notificationTitle = "早报已准备好",
            notificationBody = "早报数据完整性",
            markdown = "# 早报",
            sections = listOf(
                MorningBriefSection("今天建议优先做什么", listOf("先修复早报")),
                MorningBriefSection("今天建议优先做什么", listOf("确认 duty 配置")),
                MorningBriefSection("数据源状态", listOf("observer missing: /home/renjinxi/.nerve/plugins/observer/events/2026-06-03.jsonl (0)")),
            ),
            sources = listOf(
                MorningBriefSource(
                    kind = "daily-digest",
                    path = "/home/renjinxi/.ai/timeline/digest/2026-06-04.md",
                    available = true,
                    count = 3,
                ),
                MorningBriefSource(
                    kind = "observer",
                    path = "/home/renjinxi/.nerve/plugins/observer/events/2026-06-03.jsonl",
                    available = false,
                    count = 0,
                ),
            ),
        )

        val model = MorningBriefPresentation.from(brief = brief, loading = false, error = null)

        assertEquals(2, model.sections.count { it.title == "今天建议优先做什么" || it.title == "数据源状态" })
        assertEquals(1, model.sections.count { it.title == "今天建议优先做什么" })
        assertEquals(
            listOf("先修复早报", "确认 duty 配置"),
            model.sections.first { it.title == "今天建议优先做什么" }.items,
        )
        val diagnostics = model.sections.first { it.title == "数据源状态" }.items.joinToString("\n")
        assertTrue(diagnostics.contains("daily-digest ok"))
        assertTrue(diagnostics.contains("observer missing"))
        assertFalse(diagnostics.contains("/home/renjinxi"))
    }
}
