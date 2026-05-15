package com.nerve.android.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemeTest {

    @Test
    fun statusColor_idle_returns_StatusIdle() {
        assertEquals(StatusIdle, statusColor("idle"))
    }

    @Test
    fun statusColor_busy_returns_StatusBusy() {
        assertEquals(StatusBusy, statusColor("busy"))
    }

    @Test
    fun statusColor_error_returns_StatusError() {
        assertEquals(StatusError, statusColor("error"))
    }

    @Test
    fun statusColor_stopped_returns_StatusStopped() {
        assertEquals(StatusStopped, statusColor("stopped"))
    }

    @Test
    fun statusColor_unknown_returns_StoneMuted() {
        assertEquals(StoneMuted, statusColor("unknown"))
    }

    @Test
    fun all_status_colors_are_distinct() {
        val colors = listOf(StatusIdle, StatusBusy, StatusError, StatusStopped)
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun primary_is_amber() {
        assertEquals(AmberPrimary, AmberPrimary)
    }

    @Test
    fun background_is_cream() {
        assertEquals(CreamBackground, CreamBackground)
    }
}
