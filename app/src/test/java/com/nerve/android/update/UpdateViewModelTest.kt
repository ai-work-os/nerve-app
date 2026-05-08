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

    private fun vmWithRemote(
        versionCode: Int,
        current: Int = 1,
        downloader: ApkDownloader = ApkDownloader(),
    ): UpdateViewModel {
        val payload = """{"versionCode":$versionCode,"versionName":"x","url":"http://h/x.apk"}"""
        return UpdateViewModel(
            checker = UpdateChecker(currentVersionCode = current) { payload },
            dispatcher = dispatcher,
            downloader = downloader,
            cacheDirProvider = { java.io.File(System.getProperty("java.io.tmpdir") ?: ".") },
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
    fun `startDownload reports progress and ends in Ready when download succeeds`() = runTest(dispatcher) {
        val fakeDownloader = FakeDownloader(
            outcome = FakeOutcome.Success(bytes = 4096),
        )
        val vm = vmWithRemote(versionCode = 5, current = 1, downloader = fakeDownloader)
        vm.refresh()
        advanceUntilIdle()
        vm.startDownload()
        advanceUntilIdle()
        val state = vm.download.value as DownloadState.Ready
        assertTrue(state.file.exists())
    }

    @Test
    fun `startDownload ends in Failed when downloader fails`() = runTest(dispatcher) {
        val fakeDownloader = FakeDownloader(outcome = FakeOutcome.Failure("boom"))
        val vm = vmWithRemote(versionCode = 5, current = 1, downloader = fakeDownloader)
        vm.refresh()
        advanceUntilIdle()
        vm.startDownload()
        advanceUntilIdle()
        val state = vm.download.value
        assertTrue(state is DownloadState.Failed, "state=$state")
        assertEquals("boom", (state as DownloadState.Failed).reason)
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
