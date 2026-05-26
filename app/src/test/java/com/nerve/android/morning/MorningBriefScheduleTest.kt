package com.nerve.android.morning

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MorningBriefScheduleTest {
    @Test
    fun `next run is today at 0830 before the reminder time`() {
        val now = LocalDateTime.of(2026, 5, 26, 7, 0)
        val next = nextMorningBriefRun(now)
        assertEquals(LocalDateTime.of(2026, 5, 26, 8, 30), next)
    }

    @Test
    fun `next run moves to tomorrow after the reminder time`() {
        val now = LocalDateTime.of(2026, 5, 26, 9, 0)
        val next = nextMorningBriefRun(now)
        assertEquals(LocalDateTime.of(2026, 5, 27, 8, 30), next)
    }
}
