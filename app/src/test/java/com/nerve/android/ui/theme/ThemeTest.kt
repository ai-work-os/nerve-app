package com.nerve.android.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemeTest {

    // #16: Verify statusColor returns distinct colors for each status

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
    fun statusColor_unknown_returns_OnSurfaceVariant() {
        assertEquals(OnSurfaceVariant, statusColor("unknown"))
    }

    @Test
    fun all_status_colors_are_distinct() {
        val colors = listOf(StatusIdle, StatusBusy, StatusError, StatusStopped)
        assertEquals(colors.size, colors.toSet().size)
    }

    @Test
    fun dark_and_light_primary_are_different() {
        assertNotEquals(Primary, LightPrimary)
    }

    @Test
    fun dark_and_light_background_are_different() {
        assertNotEquals(Background, LightBackground)
    }
}
