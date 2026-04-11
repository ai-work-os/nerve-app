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
    fun `d delegates to backend`() {
        Logger.d("MyTag", "hello")
        assertEquals(1, spy.entries.size)
        val entry = spy.entries[0]
        assertEquals("d", entry.level)
        assertEquals("MyTag", entry.tag)
        assertEquals("hello", entry.message)
    }

    @Test
    fun `w delegates to backend`() {
        Logger.w("T", "warn msg")
        val entry = spy.entries[0]
        assertEquals("w", entry.level)
        assertEquals("T", entry.tag)
        assertEquals("warn msg", entry.message)
    }

    @Test
    fun `e delegates to backend with throwable`() {
        val ex = RuntimeException("boom")
        Logger.e("E", "error", ex)
        val entry = spy.entries[0]
        assertEquals("e", entry.level)
        assertEquals("E", entry.tag)
        assertEquals("error", entry.message)
        assertEquals(ex, entry.throwable)
    }

    @Test
    fun `e delegates to backend without throwable`() {
        Logger.e("E", "error")
        val entry = spy.entries[0]
        assertEquals("e", entry.level)
        assertEquals(null, entry.throwable)
    }

    @Test
    fun `PrintlnLogBackend formats output correctly`() {
        val originalOut = System.out
        val baos = java.io.ByteArrayOutputStream()
        System.setOut(java.io.PrintStream(baos))
        try {
            val backend = PrintlnLogBackend()
            backend.d("tag", "message")
            assertEquals("D/tag message\n", baos.toString())
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `detectBackend returns PrintlnLogBackend in test environment`() {
        // Android unit test stubs throw RuntimeException, so detectBackend falls back
        val backend = Logger.detectBackend()
        assertTrue(backend is PrintlnLogBackend)
    }

    private class SpyLogBackend : LogBackend {
        data class Entry(val level: String, val tag: String, val message: String, val throwable: Throwable? = null)
        val entries = mutableListOf<Entry>()

        override fun d(tag: String, message: String) { entries += Entry("d", tag, message) }
        override fun w(tag: String, message: String) { entries += Entry("w", tag, message) }
        override fun e(tag: String, message: String, throwable: Throwable?) { entries += Entry("e", tag, message, throwable) }
    }
}

class FileLogBackendTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `FileLogBackend writes log to file`() {
        val backend = FileLogBackend(logsDir = tempDir)
        backend.d("MyTag", "hello world")

        val logFiles = tempDir.listFiles()?.filter { it.extension == "log" } ?: emptyList()
        assertTrue(logFiles.isNotEmpty(), "Expected at least one log file")
        val content = logFiles.first().readText()
        assertTrue(content.contains("MyTag"), "Log file should contain tag")
        assertTrue(content.contains("hello world"), "Log file should contain message")
    }

    @Test
    fun `FileLogBackend rotates when file exceeds limit`() {
        val smallLimit = 50L // bytes
        val backend = FileLogBackend(logsDir = tempDir, maxFileSize = smallLimit)

        // Write enough to exceed the limit
        repeat(10) { i ->
            backend.d("Tag", "message number $i with some padding text")
        }

        val logFiles = tempDir.listFiles()?.filter { it.extension == "log" } ?: emptyList()
        assertTrue(logFiles.size > 1,
            "Expected multiple log files after rotation, got ${logFiles.size}")
    }

    @Test
    fun `FileLogBackend keeps only N recent files`() {
        val smallLimit = 30L
        val maxFiles = 3
        val backend = FileLogBackend(logsDir = tempDir, maxFileSize = smallLimit, maxFiles = maxFiles)

        // Write many entries to trigger multiple rotations
        repeat(50) { i ->
            backend.d("Tag", "message $i with extra padding")
        }

        val logFiles = tempDir.listFiles()?.filter { it.extension == "log" } ?: emptyList()
        assertTrue(logFiles.size <= maxFiles,
            "Expected at most $maxFiles log files, got ${logFiles.size}")
    }

    @Test
    fun `CompositeLogBackend delegates to all backends`() {
        val spy1 = SpyBackend()
        val spy2 = SpyBackend()
        val composite = CompositeLogBackend(listOf(spy1, spy2))

        composite.d("T", "msg")
        composite.w("T", "warn")
        composite.e("T", "err")

        assertEquals(3, spy1.calls.size, "spy1 should receive all 3 calls")
        assertEquals(3, spy2.calls.size, "spy2 should receive all 3 calls")
        assertEquals(listOf("d", "w", "e"), spy1.calls.map { it.first })
        assertEquals(listOf("d", "w", "e"), spy2.calls.map { it.first })
    }

    private class SpyBackend : LogBackend {
        val calls = mutableListOf<Pair<String, String>>() // level to message
        override fun d(tag: String, message: String) { calls += "d" to message }
        override fun w(tag: String, message: String) { calls += "w" to message }
        override fun e(tag: String, message: String, throwable: Throwable?) { calls += "e" to message }
    }
}
