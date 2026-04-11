package com.nerve.android.domain.dm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // --- ContentBlock tests (Step 2: will fail to compile until ContentBlock + new APIs exist) ---

    @Test
    fun `text chunks accumulate into single Text block`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onChunk(keyA, "hello", 1001L)
        accumulator.onChunk(keyA, " world", 1002L)
        val message = accumulator.onEnd(keyA, timestamp = 1003L)!!

        assertEquals(1, message.blocks.size)
        val block = assertIs<ContentBlock.Text>(message.blocks[0])
        assertEquals("hello world", block.text)
    }

    @Test
    fun `thinking chunks accumulate into Thinking block during streaming`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onThoughtChunk(keyA, "let me", 1001L)
        accumulator.onThoughtChunk(keyA, " think", 1002L)

        val blocks = accumulator.currentBlocks(keyA)
        assertEquals(1, blocks.size)
        val block = assertIs<ContentBlock.Thinking>(blocks[0])
        assertEquals("let me think", block.text)
    }

    @Test
    fun `thinking then text produces two blocks during streaming`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onThoughtChunk(keyA, "hmm", 1001L)
        accumulator.onChunk(keyA, "answer", 1002L)

        val blocks = accumulator.currentBlocks(keyA)
        assertEquals(2, blocks.size)
        assertIs<ContentBlock.Thinking>(blocks[0])
        assertEquals("hmm", (blocks[0] as ContentBlock.Thinking).text)
        assertIs<ContentBlock.Text>(blocks[1])
        assertEquals("answer", (blocks[1] as ContentBlock.Text).text)
    }

    @Test
    fun `flush preserves all block types`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onThoughtChunk(keyA, "hmm", 1001L)
        accumulator.onChunk(keyA, "answer", 1002L)
        val message = accumulator.onEnd(keyA, timestamp = 1003L)!!

        assertEquals(2, message.blocks.size)
        assertIs<ContentBlock.Thinking>(message.blocks[0])
        val textBlock = assertIs<ContentBlock.Text>(message.blocks[1])
        assertEquals("answer", textBlock.text)
        assertEquals("answer", message.content)
    }

    @Test
    fun `tool_call creates ToolCall block during streaming`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onToolCall(keyA, toolId = "1", toolName = "search", input = "q", timestamp = 1001L)

        val blocks = accumulator.currentBlocks(keyA)
        assertEquals(1, blocks.size)
        val block = assertIs<ContentBlock.ToolCall>(blocks[0])
        assertEquals("1", block.toolId)
        assertEquals("search", block.toolName)
        assertEquals("q", block.input)
    }

    @Test
    fun `tool_call implicitly closes thinking block`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onThoughtChunk(keyA, "let me", 1001L)
        accumulator.onToolCall(keyA, toolId = "1", toolName = "bash", input = "ls", timestamp = 1002L)

        val blocks = accumulator.currentBlocks(keyA)
        assertEquals(2, blocks.size)
        val thinking = assertIs<ContentBlock.Thinking>(blocks[0])
        assertTrue(thinking.completed, "Thinking block should be marked completed after tool_call")
        assertIs<ContentBlock.ToolCall>(blocks[1])
    }

    @Test
    fun `flush after idle preserves all block types`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onThoughtChunk(keyA, "hmm", 1001L)
        accumulator.onChunk(keyA, "result", 1002L)
        val message = accumulator.flush(keyA, timestamp = 1003L, reason = FlushReason.NODE_IDLE)!!

        assertEquals(2, message.blocks.size)
        assertIs<ContentBlock.Thinking>(message.blocks[0])
        val textBlock = assertIs<ContentBlock.Text>(message.blocks[1])
        assertEquals("result", textBlock.text)
        assertEquals("result", message.content)
    }

    // --- Implicit start tests ---

    @Test
    fun `chunk without prior start auto-creates streaming state`() {
        val accumulator = InMemoryStreamingAccumulator()

        // No onStart — directly onChunk
        accumulator.onChunk(keyA, "hello", 1001L)
        accumulator.onChunk(keyA, " world", 1002L)
        val message = accumulator.onEnd(keyA, timestamp = 1003L)!!

        assertEquals("hello world", message.content)
    }

    @Test
    fun `thought without prior start auto-creates streaming state`() {
        val accumulator = InMemoryStreamingAccumulator()

        // No onStart — directly onThoughtChunk
        accumulator.onThoughtChunk(keyA, "thinking...", 1001L)

        val blocks = accumulator.currentBlocks(keyA)
        assertEquals(1, blocks.size)
        assertIs<ContentBlock.Thinking>(blocks[0])
    }

    @Test
    fun `tool_call without prior start auto-creates streaming state`() {
        val accumulator = InMemoryStreamingAccumulator()

        // No onStart — directly onToolCall
        accumulator.onToolCall(keyA, toolId = "1", toolName = "bash", input = "ls", timestamp = 1001L)

        val blocks = accumulator.currentBlocks(keyA)
        assertEquals(1, blocks.size)
        assertIs<ContentBlock.ToolCall>(blocks[0])
    }

    @Test
    fun `end after implicit start produces valid message`() {
        val accumulator = InMemoryStreamingAccumulator()

        // No onStart — implicit start via chunk
        accumulator.onChunk(keyA, "answer", 1001L)
        val message = accumulator.onEnd(keyA, timestamp = 1002L)!!

        assertEquals("answer", message.content)
        assertEquals(DmRole.ASSISTANT, message.role)
    }

    // --- P0 fix: thinking/tool-only streams must not vanish ---

    @Test
    fun `flush with only thinking blocks produces message with fallback content`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onThoughtChunk(keyA, "let me think", 1001L)
        val message = accumulator.onEnd(keyA, timestamp = 1002L)

        // BUG: current impl returns null because state.text is empty (only thought wrote to blocks)
        assertNotNull(message, "Message should not be null when only thinking blocks exist")
        assertTrue(message.content.isNotBlank(), "Content should have fallback from thinking text")
    }

    @Test
    fun `flush with only tool_call produces message with fallback content`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onToolCall(keyA, toolId = "1", toolName = "Read", input = "file.txt", timestamp = 1001L)
        val message = accumulator.onEnd(keyA, timestamp = 1002L)

        // BUG: current impl returns null because state.text is empty
        assertNotNull(message, "Message should not be null when only tool_call blocks exist")
        assertTrue(message.content.isNotBlank(), "Content should have fallback from tool_call")
    }

    @Test
    fun `flush preserves all block types in message`() {
        val accumulator = InMemoryStreamingAccumulator()

        accumulator.onStart(keyA, "n1", "bot", "msg1", 1000L)
        accumulator.onThoughtChunk(keyA, "hmm", 1001L)
        accumulator.onChunk(keyA, "answer", 1002L)
        accumulator.onToolCall(keyA, toolId = "1", toolName = "bash", input = "ls", timestamp = 1003L)
        val message = accumulator.onEnd(keyA, timestamp = 1004L)!!

        // BUG: current impl filters to Text-only blocks
        assertTrue(message.blocks.size >= 3,
            "Expected at least 3 blocks (Thinking, Text, ToolCall), got ${message.blocks.size}: ${message.blocks}")
        assertTrue(message.blocks.any { it is ContentBlock.Thinking },
            "Should preserve Thinking block")
        assertTrue(message.blocks.any { it is ContentBlock.Text },
            "Should preserve Text block")
        assertTrue(message.blocks.any { it is ContentBlock.ToolCall },
            "Should preserve ToolCall block")
    }

    @Test
    fun `implicit start then end produces message`() {
        val accumulator = InMemoryStreamingAccumulator()

        // No onStart — implicit via chunk
        accumulator.onChunk(keyA, "reply", 1001L)
        val message = accumulator.onEnd(keyA, timestamp = 1002L)

        assertNotNull(message, "Implicit start should still produce a message on end")
        assertEquals("reply", message.content)
        assertEquals(DmRole.ASSISTANT, message.role)
    }
}
