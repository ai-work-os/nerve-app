package com.nerve.android.util

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

enum class LogLevel { DEBUG, WARN, ERROR }

interface LogBackend {
    fun write(level: LogLevel, tag: String, line: String, throwable: Throwable? = null)
}

class AndroidLogBackend : LogBackend {
    override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
        when (level) {
            LogLevel.DEBUG -> android.util.Log.d(tag, line)
            LogLevel.WARN -> android.util.Log.w(tag, line)
            LogLevel.ERROR -> {
                if (throwable != null) android.util.Log.e(tag, line, throwable)
                else android.util.Log.e(tag, line)
            }
        }
    }
}

class PrintlnLogBackend : LogBackend {
    override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
        println("${prefix(level)}/$tag $line")
        throwable?.printStackTrace()
    }
}

class FileLogBackend(
    private val logsDir: java.io.File,
    private val maxFileSize: Long = 1_000_000L,
    private val maxFiles: Int = 5,
) : LogBackend {
    private var currentFile: java.io.File = newLogFile()

    override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
        writeLine("${prefix(level)}/$tag $line")
        throwable?.let { writeLine(it.stackTraceToString()) }
    }

    @Synchronized
    private fun writeLine(line: String) {
        currentFile.appendText("${Instant.now()} $line\n")
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
    override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
        backends.forEach { it.write(level, tag, line, throwable) }
    }

    fun flatten(): List<LogBackend> = backends.flatMap { b ->
        if (b is CompositeLogBackend) b.flatten() else listOf(b)
    }
}

object Logger {
    var backend: LogBackend = detectBackend()

    fun debug(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        backend.write(LogLevel.DEBUG, tag, format(event, fields))
    }

    fun warn(
        tag: String,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        backend.write(LogLevel.WARN, tag, format(event, fields), throwable)
    }

    fun error(
        tag: String,
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        backend.write(LogLevel.ERROR, tag, format(event, fields), throwable)
    }

    fun init(context: Context) {
        initFileLogging(java.io.File(context.filesDir, "logs"), AndroidLogBackend())
    }

    fun initFileLogging(logsDir: java.io.File, primaryBackend: LogBackend = detectBackend()) {
        backend = CompositeLogBackend(
            listOf(
                primaryBackend,
                FileLogBackend(logsDir),
            ),
        )
        debug(
            "Logger",
            "logger_initialized",
            mapOf("fileLogging" to true, "dir" to logsDir.absolutePath),
        )
    }

    /**
     * Append a remote backend (e.g. RemoteLogBackend) onto the existing chain.
     * Used to ship WARN+ERROR to a collector without disturbing the local
     * file/log backends.
     */
    fun addBackend(extra: LogBackend) {
        val current = backend
        backend = if (current is CompositeLogBackend) {
            CompositeLogBackend(current.flatten() + extra)
        } else {
            CompositeLogBackend(listOf(current, extra))
        }
        debug("Logger", "backend_added", mapOf("type" to extra::class.simpleName))
    }

    fun detectBackend(): LogBackend = try {
        Class.forName("android.util.Log")
        // Verify it actually works (Android unit test stubs throw RuntimeException)
        android.util.Log.isLoggable("Logger", android.util.Log.DEBUG)
        AndroidLogBackend()
    } catch (_: Throwable) {
        PrintlnLogBackend()
    }

    private fun format(event: String, fields: Map<String, Any?>): String {
        val parts = mutableListOf("event=$event")
        fields.forEach { (key, value) ->
            if (value != null) parts += "$key=${formatValue(value)}"
        }
        return parts.joinToString(" ")
    }

    private fun formatValue(value: Any): String {
        if (value !is String) return value.toString()
        return if (value.any { it.isWhitespace() || it == '"' || it == '\\' || it == '=' }) {
            Json.encodeToString(value)
        } else {
            value
        }
    }
}

private fun prefix(level: LogLevel): String = when (level) {
    LogLevel.DEBUG -> "D"
    LogLevel.WARN -> "W"
    LogLevel.ERROR -> "E"
}
