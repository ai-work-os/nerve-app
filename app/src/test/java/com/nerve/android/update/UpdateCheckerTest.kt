package com.nerve.android.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateCheckerTest {
    private fun checker(currentVersionCode: Int, payload: Result<String>): UpdateChecker =
        UpdateChecker(
            currentVersionCode = currentVersionCode,
            fetchPayload = { payload.getOrThrow() },
        )

    @Test
    fun `returns Available when remote is newer`() = runTest {
        val payload = """{"versionCode":3,"versionName":"0.3.0","url":"http://h/x.apk","notes":"new"}"""
        val result = checker(currentVersionCode = 2, payload = Result.success(payload)).check()
        val available = result as UpdateState.Available
        assertEquals(3, available.info.versionCode)
        assertEquals("0.3.0", available.info.versionName)
    }

    @Test
    fun `returns UpToDate when remote equals current`() = runTest {
        val payload = """{"versionCode":2,"versionName":"0.2.0","url":"http://h/x.apk"}"""
        val result = checker(currentVersionCode = 2, payload = Result.success(payload)).check()
        assertEquals(UpdateState.UpToDate, result)
    }

    @Test
    fun `returns UpToDate when remote is older - never offers downgrade`() = runTest {
        val payload = """{"versionCode":1,"versionName":"0.1.0","url":"http://h/x.apk"}"""
        val result = checker(currentVersionCode = 2, payload = Result.success(payload)).check()
        assertEquals(UpdateState.UpToDate, result)
    }

    @Test
    fun `returns Unknown when fetch throws`() = runTest {
        val result = checker(
            currentVersionCode = 2,
            payload = Result.failure(RuntimeException("network down")),
        ).check()
        assertEquals(UpdateState.Unknown, result)
    }

    @Test
    fun `returns Unknown when payload is unparseable`() = runTest {
        val result = checker(currentVersionCode = 2, payload = Result.success("not json")).check()
        assertEquals(UpdateState.Unknown, result)
    }
}
