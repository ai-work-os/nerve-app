package com.nerve.android

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertTrue

class JvmTestBootstrapTest {
    @Test
    @DisplayName("jupiter executes grouped assertions on plain JVM code")
    fun jupiterExecutesJvmTest() {
        assertAll(
            { assertTrue("stage0".startsWith("stage")) },
            { assertTrue("jupiter".contains("iter")) },
        )
    }
}
