package com.nerve.android.update

import java.io.File

sealed interface FakeOutcome {
    data class Success(val bytes: Int) : FakeOutcome
    data class Failure(val message: String) : FakeOutcome
}

class FakeDownloader(private val outcome: FakeOutcome) : ApkDownloader() {
    override suspend fun download(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File> = when (val o = outcome) {
        is FakeOutcome.Success -> {
            dest.parentFile?.mkdirs()
            dest.writeBytes(ByteArray(o.bytes))
            onProgress(o.bytes.toLong(), o.bytes.toLong())
            Result.success(dest)
        }
        is FakeOutcome.Failure -> Result.failure(RuntimeException(o.message))
    }
}
