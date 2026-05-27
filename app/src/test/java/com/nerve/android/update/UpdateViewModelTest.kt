package com.nerve.android.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var cacheDir: File

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
        cacheDir = Files.createTempDirectory("nerve-update-test-").toFile()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
        cacheDir.deleteRecursively()
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
            cacheDirProvider = { cacheDir },
        )
    }

    private fun vmWithRemoteSequence(
        versionCodes: List<Int>,
        current: Int = 1,
        downloader: ApkDownloader = ApkDownloader(),
    ): UpdateViewModel {
        var index = 0
        return UpdateViewModel(
            checker = UpdateChecker(currentVersionCode = current) {
                val versionCode = versionCodes[index.coerceAtMost(versionCodes.lastIndex)]
                index += 1
                """{"versionCode":$versionCode,"versionName":"x","url":"http://h/x.apk"}"""
            },
            dispatcher = dispatcher,
            downloader = downloader,
            cacheDirProvider = { cacheDir },
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

    @Test
    fun `dismiss from Ready records version and clears download`() = runTest(dispatcher) {
        val fakeDownloader = FakeDownloader(outcome = FakeOutcome.Success(bytes = 4096))
        val vm = vmWithRemote(versionCode = 5, current = 1, downloader = fakeDownloader)
        vm.refresh()
        advanceUntilIdle()
        vm.startDownload()
        advanceUntilIdle()
        assertTrue(vm.download.value is DownloadState.Ready)

        vm.dismiss()

        assertEquals(5, vm.dismissedVersionCode.value)
        assertEquals(DownloadState.Idle, vm.download.value)
    }

    @Test
    fun `refresh to UpToDate clears stale Ready download`() = runTest(dispatcher) {
        val fakeDownloader = FakeDownloader(outcome = FakeOutcome.Success(bytes = 4096))
        val vm = vmWithRemoteSequence(versionCodes = listOf(5, 1), current = 1, downloader = fakeDownloader)
        vm.refresh()
        advanceUntilIdle()
        vm.startDownload()
        advanceUntilIdle()
        assertTrue(vm.download.value is DownloadState.Ready)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(UpdateState.UpToDate, vm.state.value)
        assertEquals(DownloadState.Idle, vm.download.value)
    }

    @Test
    fun `install launched resets Ready download for retry after returning`() = runTest(dispatcher) {
        val fakeDownloader = FakeDownloader(outcome = FakeOutcome.Success(bytes = 4096))
        val vm = vmWithRemote(versionCode = 5, current = 1, downloader = fakeDownloader)
        vm.refresh()
        advanceUntilIdle()
        vm.startDownload()
        advanceUntilIdle()
        assertTrue(vm.download.value is DownloadState.Ready)

        vm.installLaunched()

        assertEquals(DownloadState.Idle, vm.download.value)
        assertNull(vm.dismissedVersionCode.value)
        assertTrue(vm.state.value is UpdateState.Available)
    }
}
