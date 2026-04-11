package com.nerve.android.domain.dm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StreamingAccumulatorTest {
    private val keyA = DmKey("s1:n1")
    private val keyB = DmKey("s1:n2")

    @Test
    fun `start chunks end yields one assistant message`() {
        val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "stream-1", 100L)
        accumulator.onChunk(keyA, "hel", 110L)
        accumulator.onChunk(keyA, "lo", 120L)
        val result = accumulator.onEnd(keyA, timestamp = 130L)

        requireNotNull(result)
        assertEquals(DmRole.ASSISTANT, result.role)
        assertEquals("hello", result.content)
        assertEquals(130L, result.timestamp)
    }

    @Test
    fun `start chunk idle flush yields one assistant message`() {
        val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "stream-1", 100L)
        accumulator.onChunk(keyA, "hello", 120L)
        val result = accumulator.flush(keyA, 140L, FlushReason.NODE_IDLE)

        requireNotNull(result)
        assertEquals("hello", result.content)
        assertEquals(140L, result.timestamp)
    }

    @Test
    fun `start end with fallback text uses fallback`() {
        val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "stream-1", 100L)
        val result = accumulator.onEnd(keyA, fallbackText = "full text", timestamp = 130L)

        requireNotNull(result)
        assertEquals("full text", result.content)
        assertEquals(130L, result.timestamp)
    }

    @Test
    fun `empty assistant does not emit on idle or end`() {
        val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "stream-1", 100L)
        assertNull(accumulator.flush(keyA, 120L, FlushReason.NODE_IDLE))

        accumulator.onStart(keyA, "n1", "bot", "stream-2", 130L)
        assertNull(accumulator.onEnd(keyA, timestamp = 140L))
    }

    @Test
    fun `user flush emits current assistant before next message`() {
        val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "stream-1", 100L)
        accumulator.onChunk(keyA, "hello", 110L)
        val result = accumulator.flush(keyA, 120L, FlushReason.USER_MESSAGE)

        requireNotNull(result)
        assertEquals("hello", result.content)
        assertEquals(110L, result.timestamp)
    }

    @Test
    fun `user flush keeps assistant last event time`() {
        val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "stream-1", 100L)
        accumulator.onChunk(keyA, "hello", 110L)
        val result = accumulator.flush(keyA, 999L, FlushReason.USER_MESSAGE)

        requireNotNull(result)
        assertEquals("hello", result.content)
        assertEquals(110L, result.timestamp)
    }

    @Test
    fun `different keys keep streams isolated`() {
        val accumulator: StreamingAccumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot-a", "stream-1", 100L)
        accumulator.onChunk(keyA, "a", 101L)
        accumulator.onStart(keyB, "n2", "bot-b", "stream-2", 200L)
        accumulator.onChunk(keyB, "b", 201L)

        assertEquals("a", accumulator.onEnd(keyA, timestamp = 102L)?.content)
        assertEquals("b", accumulator.onEnd(keyB, timestamp = 202L)?.content)
    }
}
