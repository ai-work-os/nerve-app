package com.nerve.android.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LoggerTest {

    private lateinit var spy: SpyLogBackend

    @BeforeEach
    fun setUp() {
        spy = SpyLogBackend()
        Logger.backend = spy
    }

    @AfterEach
    fun tearDown() {
        Logger.backend = Logger.detectBackend()
    }

    @Test
    fun `debug formats event and fields`() {
        Logger.debug("MyTag", "rpc_send", mapOf("method" to "node.prompt", "requestId" to 12L))

        assertEquals(1, spy.entries.size)
        val entry = spy.entries[0]
        assertEquals(LogLevel.DEBUG, entry.level)
        assertEquals("MyTag", entry.tag)
        assertEquals("event=rpc_send method=node.prompt requestId=12", entry.line)
        assertEquals(null, entry.throwable)
    }

    @Test
    fun `warn omits null fields`() {
        Logger.warn("T", "refresh_fail", mapOf("serverId" to "local", "reason" to null))

        assertEquals("event=refresh_fail serverId=local", spy.entries[0].line)
    }

    @Test
    fun `error passes throwable`() {
        val ex = RuntimeException("boom")

        Logger.error("E", "socket_failure", mapOf("reason" to ex.message), ex)

        val entry = spy.entries[0]
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("event=socket_failure reason=boom", entry.line)
        assertEquals(ex, entry.throwable)
    }

    @Test
    fun `strings needing escaping are JSON strings`() {
        Logger.debug(
            "Escape",
            "field_escape",
            mapOf(
                "space" to "hello world",
                "equals" to "a=b",
                "newline" to "a\nb",
                "quote" to "say \"hi\"",
                "backslash" to "a\\b",
            ),
        )

        assertEquals(
            "event=field_escape space=\"hello world\" equals=\"a=b\" newline=\"a\\nb\" quote=\"say \\\"hi\\\"\" backslash=\"a\\\\b\"",
            spy.entries[0].line,
        )
    }

    @Test
    fun `PrintlnLogBackend formats output correctly`() {
        val originalOut = System.out
        val baos = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(baos))
        try {
            val backend = PrintlnLogBackend()
            backend.write(LogLevel.DEBUG, "tag", "event=test_event")
            assertEquals("D/tag event=test_event\n", baos.toString())
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `detectBackend returns PrintlnLogBackend in test environment`() {
        val backend = Logger.detectBackend()
        assertTrue(backend is PrintlnLogBackend)
    }

    @Test
    fun `initFileLogging writes to android and file backends`(@TempDir tempDir: File) {
        val androidSpy = SpyLogBackend()

        Logger.initFileLogging(tempDir, androidSpy)
        Logger.debug("Init", "persist_test")

        assertEquals(2, androidSpy.entries.size)
        assertEquals("event=logger_initialized fileLogging=true dir=${tempDir.absolutePath}", androidSpy.entries[0].line)
        assertEquals("event=persist_test", androidSpy.entries[1].line)

        val content = tempDir.listFiles()?.filter { it.extension == "log" }
            ?.joinToString("\n") { it.readText() }
            .orEmpty()
        assertTrue(content.contains("event=logger_initialized fileLogging=true"), "file log should contain init line")
        assertTrue(content.contains("D/Init event=persist_test"), "file log should contain app log line")
    }

    private class SpyLogBackend : LogBackend {
        data class Entry(
            val level: LogLevel,
            val tag: String,
            val line: String,
            val throwable: Throwable? = null,
        )
        val entries = mutableListOf<Entry>()

        override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
            entries += Entry(level, tag, line, throwable)
        }
    }
}

class FileLogBackendTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `FileLogBackend writes log to file`() {
        val backend = FileLogBackend(logsDir = tempDir)
        backend.write(LogLevel.DEBUG, "MyTag", "event=test_event message=\"hello world\"")

        val logFiles = tempDir.listFiles()?.filter { it.extension == "log" } ?: emptyList()
        assertTrue(logFiles.isNotEmpty(), "Expected at least one log file")
        val content = logFiles.first().readText()
        assertTrue(content.contains(Regex("""\d{4}-\d{2}-\d{2}T""")), "Log file should contain timestamp")
        assertTrue(content.contains("MyTag"), "Log file should contain tag")
        assertTrue(content.contains("event=test_event message=\"hello world\""), "Log file should contain structured line")
    }

    @Test
    fun `FileLogBackend rotates when file exceeds limit`() {
        val smallLimit = 50L
        val backend = FileLogBackend(logsDir = tempDir, maxFileSize = smallLimit)

        repeat(10) { i ->
            backend.write(LogLevel.DEBUG, "Tag", "event=test_event index=$i padding=\"some padding text\"")
        }

        val logFiles = tempDir.listFiles()?.filter { it.extension == "log" } ?: emptyList()
        assertTrue(
            logFiles.size > 1,
            "Expected multiple log files after rotation, got ${logFiles.size}",
        )
    }

    @Test
    fun `FileLogBackend keeps only N recent files`() {
        val smallLimit = 30L
        val maxFiles = 3
        val backend = FileLogBackend(logsDir = tempDir, maxFileSize = smallLimit, maxFiles = maxFiles)

        repeat(50) { i ->
            backend.write(LogLevel.DEBUG, "Tag", "event=test_event index=$i padding=\"extra padding\"")
        }

        val logFiles = tempDir.listFiles()?.filter { it.extension == "log" } ?: emptyList()
        assertTrue(
            logFiles.size <= maxFiles,
            "Expected at most $maxFiles log files, got ${logFiles.size}",
        )
    }

    @Test
    fun `CompositeLogBackend delegates to all backends`() {
        val spy1 = SpyBackend()
        val spy2 = SpyBackend()
        val composite = CompositeLogBackend(listOf(spy1, spy2))

        composite.write(LogLevel.DEBUG, "T", "event=debug_event")
        composite.write(LogLevel.WARN, "T", "event=warn_event")
        composite.write(LogLevel.ERROR, "T", "event=error_event")

        assertEquals(3, spy1.calls.size, "spy1 should receive all 3 calls")
        assertEquals(3, spy2.calls.size, "spy2 should receive all 3 calls")
        assertEquals(listOf(LogLevel.DEBUG, LogLevel.WARN, LogLevel.ERROR), spy1.calls.map { it.level })
        assertEquals(listOf(LogLevel.DEBUG, LogLevel.WARN, LogLevel.ERROR), spy2.calls.map { it.level })
    }

    private class SpyBackend : LogBackend {
        data class Call(
            val level: LogLevel,
            val tag: String,
            val line: String,
            val throwable: Throwable?,
        )
        val calls = mutableListOf<Call>()

        override fun write(level: LogLevel, tag: String, line: String, throwable: Throwable?) {
            calls += Call(level, tag, line, throwable)
        }
    }
}
