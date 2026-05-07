package com.nerve.android.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionInfoTest {
    @Test
    fun `parse complete payload`() {
        val json = """
            {"versionCode":3,"versionName":"0.3.0","url":"http://h/nerve-app.apk","notes":"图片缓冲"}
        """.trimIndent()
        val info = AppVersionInfo.parse(json)!!
        assertEquals(3, info.versionCode)
        assertEquals("0.3.0", info.versionName)
        assertEquals("http://h/nerve-app.apk", info.url)
        assertEquals("图片缓冲", info.notes)
    }

    @Test
    fun `parse without notes uses null`() {
        val json = """{"versionCode":2,"versionName":"0.2.0","url":"http://h/x.apk"}"""
        val info = AppVersionInfo.parse(json)!!
        assertNull(info.notes)
    }

    @Test
    fun `parse invalid returns null`() {
        assertNull(AppVersionInfo.parse("not json"))
    }

    @Test
    fun `parse missing required fields returns null`() {
        assertNull(AppVersionInfo.parse("""{"versionCode":1}"""))
    }

    @Test
    fun `isNewer when remote is bigger`() {
        val info = AppVersionInfo(versionCode = 5, versionName = "x", url = "u", notes = null)
        assertTrue(info.isNewerThan(currentVersionCode = 4))
    }

    @Test
    fun `isNewer false when equal`() {
        val info = AppVersionInfo(versionCode = 5, versionName = "x", url = "u", notes = null)
        assertEquals(false, info.isNewerThan(currentVersionCode = 5))
    }

    @Test
    fun `isNewer false when remote older - never downgrade`() {
        val info = AppVersionInfo(versionCode = 4, versionName = "x", url = "u", notes = null)
        assertEquals(false, info.isNewerThan(currentVersionCode = 5))
    }
}
