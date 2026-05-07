package com.nerve.android.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun vmWithRemote(versionCode: Int, current: Int = 1): UpdateViewModel {
        val payload = """{"versionCode":$versionCode,"versionName":"x","url":"http://h/x.apk"}"""
        return UpdateViewModel(
            checker = UpdateChecker(currentVersionCode = current) { payload },
            dispatcher = dispatcher,
        )
    }

    @Test
    fun `state begins Unknown`() {
        val vm = vmWithRemote(versionCode = 5)
        assertEquals(UpdateState.Unknown, vm.state.value)
    }

    @Test
    fun `refresh transitions to Available when remote newer`() = runTest(dispatcher) {
        val vm = vmWithRemote(versionCode = 5, current = 1)
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value is UpdateState.Available)
    }

    @Test
    fun `refresh transitions to UpToDate when remote equals`() = runTest(dispatcher) {
        val vm = vmWithRemote(versionCode = 1, current = 1)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(UpdateState.UpToDate, vm.state.value)
    }

    @Test
    fun `dismiss records the dismissed versionCode`() = runTest(dispatcher) {
        val vm = vmWithRemote(versionCode = 5, current = 1)
        vm.refresh()
        advanceUntilIdle()
        assertNull(vm.dismissedVersionCode.value)
        vm.dismiss()
        assertEquals(5, vm.dismissedVersionCode.value)
    }
}
