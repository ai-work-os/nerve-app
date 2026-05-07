package com.nerve.android.ui.nodes

import kotlin.test.Test
import kotlin.test.assertEquals

class SpawnDialogDefaultsTest {
    @Test
    fun `default adapter is codex`() {
        assertEquals("codex", SpawnDialogDefaults.ADAPTER)
    }
}
