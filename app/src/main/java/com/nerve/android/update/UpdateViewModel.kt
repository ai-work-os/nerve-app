package com.nerve.android.update

import androidx.lifecycle.ViewModel
import com.nerve.android.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface DownloadState {
    data object Idle : DownloadState
    data class InProgress(val downloaded: Long, val total: Long) : DownloadState
    data class Ready(val file: File) : DownloadState
    data class Failed(val reason: String?) : DownloadState
}

class UpdateViewModel(
    private val checker: UpdateChecker,
    dispatcher: CoroutineDispatcher,
    private val downloader: ApkDownloader = ApkDownloader(),
    private val cacheDirProvider: () -> File = { File(System.getProperty("java.io.tmpdir") ?: ".") },
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _dismissedVersionCode = MutableStateFlow<Int?>(null)
    val dismissedVersionCode: StateFlow<Int?> = _dismissedVersionCode.asStateFlow()

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    private var downloadJob: Job? = null

    fun refresh() {
        scope.launch {
            Logger.debug("UpdateViewModel", "refresh_begin", emptyMap())
            _state.value = checker.check()
        }
    }

    fun dismiss() {
        val current = _state.value
        if (current is UpdateState.Available) {
            _dismissedVersionCode.value = current.info.versionCode
            Logger.debug(
                "UpdateViewModel",
                "dismiss",
                mapOf("versionCode" to current.info.versionCode),
            )
        }
    }

    fun startDownload() {
        val available = _state.value as? UpdateState.Available ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            val dest = File(cacheDirProvider(), "updates/nerve-app-${available.info.versionCode}.apk")
            _download.value = DownloadState.InProgress(downloaded = 0L, total = -1L)
            val result = downloader.download(
                url = available.info.url,
                dest = dest,
                onProgress = { downloaded, total ->
                    _download.value = DownloadState.InProgress(downloaded, total)
                },
            )
            _download.value = result.fold(
                onSuccess = { DownloadState.Ready(it) },
                onFailure = { DownloadState.Failed(it.message) },
            )
        }
    }

    fun resetDownload() {
        downloadJob?.cancel()
        _download.value = DownloadState.Idle
    }
}
