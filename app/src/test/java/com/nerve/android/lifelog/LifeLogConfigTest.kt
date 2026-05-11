package com.nerve.android.lifelog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LifeLogConfigTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test fun `default empty url and token`() {
    val cfg = LifeLogConfig(context)
    assertEquals("", cfg.homeUrl)
    assertEquals("", cfg.token)
  }

  @Test fun `persists set values`() {
    val cfg = LifeLogConfig(context)
    cfg.homeUrl = "http://100.75.43.90:4810"
    cfg.token = "secret"
    val cfg2 = LifeLogConfig(context)
    assertEquals("http://100.75.43.90:4810", cfg2.homeUrl)
    assertEquals("secret", cfg2.token)
  }

  @Test fun `generates stable deviceId across instances`() {
    val cfg = LifeLogConfig(context)
    val id1 = cfg.deviceId
    assertTrue(id1.isNotEmpty())
    val cfg2 = LifeLogConfig(context)
    assertEquals(id1, cfg2.deviceId)
  }
}
