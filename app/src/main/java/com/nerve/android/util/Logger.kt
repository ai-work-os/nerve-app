package com.nerve.android.util

interface LogBackend {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

class AndroidLogBackend : LogBackend {
    override fun d(tag: String, message: String) { android.util.Log.d(tag, message) }
    override fun w(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) android.util.Log.e(tag, message, throwable)
        else android.util.Log.e(tag, message)
    }
}

class PrintlnLogBackend : LogBackend {
    override fun d(tag: String, message: String) { println("D/$tag $message") }
    override fun w(tag: String, message: String) { println("W/$tag $message") }
    override fun e(tag: String, message: String, throwable: Throwable?) {
        println("E/$tag $message")
        throwable?.printStackTrace()
    }
}

class FileLogBackend(
    private val logsDir: java.io.File,
    private val maxFileSize: Long = 1_000_000L,
    private val maxFiles: Int = 5,
) : LogBackend {
    private var currentFile: java.io.File = newLogFile()

    override fun d(tag: String, message: String) = write("D/$tag $message")
    override fun w(tag: String, message: String) = write("W/$tag $message")
    override fun e(tag: String, message: String, throwable: Throwable?) {
        write("E/$tag $message")
        throwable?.let { write(it.stackTraceToString()) }
    }

    @Synchronized
    private fun write(line: String) {
        currentFile.appendText(line + "\n")
        if (currentFile.length() > maxFileSize) {
            currentFile = newLogFile()
            pruneOldFiles()
        }
    }

    private fun newLogFile(): java.io.File {
        logsDir.mkdirs()
        return java.io.File(logsDir, "nerve-${System.nanoTime()}.log")
    }

    private fun pruneOldFiles() {
        val files = logsDir.listFiles { f -> f.extension == "log" }
            ?.sortedBy { it.lastModified() } ?: return
        val excess = files.size - maxFiles
        if (excess > 0) {
            files.take(excess).forEach { it.delete() }
        }
    }
}

class CompositeLogBackend(private val backends: List<LogBackend>) : LogBackend {
    override fun d(tag: String, message: String) = backends.forEach { it.d(tag, message) }
    override fun w(tag: String, message: String) = backends.forEach { it.w(tag, message) }
    override fun e(tag: String, message: String, throwable: Throwable?) = backends.forEach { it.e(tag, message, throwable) }
}

object Logger {
    var backend: LogBackend = detectBackend()

    fun d(tag: String, message: String) = backend.d(tag, message)
    fun w(tag: String, message: String) = backend.w(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = backend.e(tag, message, throwable)

    fun detectBackend(): LogBackend = try {
        Class.forName("android.util.Log")
        // Verify it actually works (Android unit test stubs throw RuntimeException)
        android.util.Log.isLoggable("Logger", android.util.Log.DEBUG)
        AndroidLogBackend()
    } catch (_: Throwable) {
        PrintlnLogBackend()
    }
}
