package com.nerve.android.domain.dm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DmSessionManagerTest {

    private fun manager() = DmSessionManager()

    // --- helpers ---

    private fun start(nodeId: String = "n1", nodeName: String = "bot", ts: Long = 100L, msgId: String = "msg1") =
        DmMappedEvent.AgentMessageStart(nodeId = nodeId, nodeName = nodeName, timestamp = ts, messageId = msgId)

    private fun chunk(text: String, nodeId: String = "n1", ts: Long = 110L) =
        DmMappedEvent.AgentMessageChunk(nodeId = nodeId, text = text, timestamp = ts)

    private fun end(nodeId: String = "n1", nodeName: String = "bot", ts: Long = 130L, fallback: String? = null) =
        DmMappedEvent.AgentMessageEnd(nodeId = nodeId, nodeName = nodeName, timestamp = ts, fallbackText = fallback)

    private fun userMsg(content: String, nodeId: String = "n1", nodeName: String = "user", ts: Long = 200L, msgId: String = "u1") =
        DmMappedEvent.UserMessage(nodeId = nodeId, nodeName = nodeName, content = content, timestamp = ts, messageId = msgId)

    private fun idle(nodeId: String = "n1", ts: Long = 150L) =
        DmMappedEvent.NodeIdle(nodeId = nodeId, timestamp = ts)

    private fun thoughtChunk(text: String, nodeId: String = "n1", ts: Long = 110L) =
        DmMappedEvent.AgentThoughtChunk(nodeId = nodeId, text = text, timestamp = ts)

    private fun toolCall(toolId: String = "1", toolName: String = "bash", input: String = "ls", nodeId: String = "n1", ts: Long = 110L) =
        DmMappedEvent.ToolCall(nodeId = nodeId, toolId = toolId, toolName = toolName, input = input, timestamp = ts)

    // === 正常流 ===

    @Test
    fun `start chunks end produces finalized message in messages`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("hel", ts = 110L))
        mgr.onEvent(chunk("lo", ts = 120L))
        mgr.onEvent(end(ts = 130L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals(DmRole.ASSISTANT, msgs[0].role)
        assertEquals("hello", msgs[0].content)
        assertEquals(130L, msgs[0].timestamp)
    }

    @Test
    fun `streamingMessage updates on each chunk`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("h", ts = 110L))
        assertNotNull(mgr.streamingMessage.value)
        assertEquals("h", mgr.streamingMessage.value!!.content)

        mgr.onEvent(chunk("i", ts = 120L))
        assertEquals("hi", mgr.streamingMessage.value!!.content)
    }

    @Test
    fun `end clears streamingMessage to null`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("hello", ts = 110L))
        assertNotNull(mgr.streamingMessage.value)

        mgr.onEvent(end(ts = 130L))
        assertNull(mgr.streamingMessage.value)
    }

    // === 隐式 start ===

    @Test
    fun `chunk without start auto-creates streamingMessage`() {
        val mgr = manager()

        mgr.onEvent(chunk("hello", ts = 110L))
        assertNotNull(mgr.streamingMessage.value)
        assertEquals("hello", mgr.streamingMessage.value!!.content)
    }

    @Test
    fun `thought_chunk without start auto-creates streamingMessage`() {
        val mgr = manager()

        mgr.onEvent(thoughtChunk("hmm", ts = 110L))
        val streaming = mgr.streamingMessage.value
        assertNotNull(streaming)
        assertTrue(streaming.blocks.any { it is ContentBlock.Thinking })
    }

    @Test
    fun `tool_call without start auto-creates streamingMessage`() {
        val mgr = manager()

        mgr.onEvent(toolCall(ts = 110L))
        val streaming = mgr.streamingMessage.value
        assertNotNull(streaming)
        assertTrue(streaming.blocks.any { it is ContentBlock.ToolCall })
    }

    @Test
    fun `implicit start then end produces valid finalized message`() {
        val mgr = manager()

        mgr.onEvent(chunk("reply", ts = 110L))
        mgr.onEvent(end(ts = 130L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals(DmRole.ASSISTANT, msgs[0].role)
        assertEquals("reply", msgs[0].content)
        assertEquals(130L, msgs[0].timestamp)
    }

    // === ContentBlock 拼接 ===

    @Test
    fun `text chunks accumulate into single Text block`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("hello", ts = 110L))
        mgr.onEvent(chunk(" world", ts = 120L))

        val streaming = mgr.streamingMessage.value!!
        val textBlocks = streaming.blocks.filterIsInstance<ContentBlock.Text>()
        assertEquals(1, textBlocks.size)
        assertEquals("hello world", textBlocks[0].text)
    }

    @Test
    fun `thought_chunks accumulate into Thinking block`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(thoughtChunk("let me", ts = 110L))
        mgr.onEvent(thoughtChunk(" think", ts = 120L))

        val streaming = mgr.streamingMessage.value!!
        val thinkBlocks = streaming.blocks.filterIsInstance<ContentBlock.Thinking>()
        assertEquals(1, thinkBlocks.size)
        assertEquals("let me think", thinkBlocks[0].text)
    }

    @Test
    fun `thinking then text produces two blocks`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(thoughtChunk("hmm", ts = 110L))
        mgr.onEvent(chunk("answer", ts = 120L))

        val streaming = mgr.streamingMessage.value!!
        assertEquals(2, streaming.blocks.size)
        assertIs<ContentBlock.Thinking>(streaming.blocks[0])
        assertIs<ContentBlock.Text>(streaming.blocks[1])
    }

    @Test
    fun `tool_call appends ToolCall block`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(toolCall(toolId = "1", toolName = "search", input = "q", ts = 110L))

        val streaming = mgr.streamingMessage.value!!
        val block = assertIs<ContentBlock.ToolCall>(streaming.blocks.last())
        assertEquals("1", block.toolId)
        assertEquals("search", block.toolName)
        assertEquals("q", block.input)
    }

    @Test
    fun `tool_call implicitly closes thinking block`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(thoughtChunk("let me", ts = 110L))
        mgr.onEvent(toolCall(ts = 120L))

        val streaming = mgr.streamingMessage.value!!
        val thinking = assertIs<ContentBlock.Thinking>(streaming.blocks[0])
        assertTrue(thinking.completed, "Thinking block should be marked completed after tool_call")
        assertIs<ContentBlock.ToolCall>(streaming.blocks[1])
    }

    @Test
    fun `all block types preserved in finalized message`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(thoughtChunk("hmm", ts = 110L))
        mgr.onEvent(chunk("answer", ts = 120L))
        mgr.onEvent(toolCall(ts = 130L))
        mgr.onEvent(end(ts = 140L))

        val msg = mgr.messages.value.last()
        assertTrue(msg.blocks.size >= 3)
        assertTrue(msg.blocks.any { it is ContentBlock.Thinking })
        assertTrue(msg.blocks.any { it is ContentBlock.Text })
        assertTrue(msg.blocks.any { it is ContentBlock.ToolCall })
    }

    // === User message flush ===

    @Test
    fun `user message flushes streaming to messages then appends user`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("hello", ts = 110L))
        mgr.onEvent(userMsg("hi", ts = 200L))

        val msgs = mgr.messages.value
        assertEquals(2, msgs.size)
        assertEquals(DmRole.ASSISTANT, msgs[0].role)
        assertEquals("hello", msgs[0].content)
        assertEquals(110L, msgs[0].timestamp)
        assertEquals(DmRole.USER, msgs[1].role)
        assertEquals("hi", msgs[1].content)
        assertNull(mgr.streamingMessage.value)
    }

    @Test
    fun `user message with empty streaming only appends user`() {
        val mgr = manager()

        mgr.onEvent(userMsg("hi", ts = 200L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals(DmRole.USER, msgs[0].role)
    }

    // === Node idle flush ===

    @Test
    fun `node_idle flushes streaming to messages`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("hello", ts = 110L))
        mgr.onEvent(idle(ts = 150L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals(DmRole.ASSISTANT, msgs[0].role)
        assertEquals("hello", msgs[0].content)
        assertEquals(150L, msgs[0].timestamp)
        assertNull(mgr.streamingMessage.value)
    }

    @Test
    fun `node_idle with empty streaming does nothing`() {
        val mgr = manager()

        mgr.onEvent(idle(ts = 150L))

        assertTrue(mgr.messages.value.isEmpty())
        assertNull(mgr.streamingMessage.value)
    }

    // === end 边界 ===

    @Test
    fun `end without active streaming is no-op`() {
        val mgr = manager()

        mgr.onEvent(end(ts = 130L))

        assertTrue(mgr.messages.value.isEmpty())
        assertNull(mgr.streamingMessage.value)
    }

    @Test
    fun `duplicate end does not crash or duplicate message`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("hello", ts = 110L))
        mgr.onEvent(end(ts = 130L))
        mgr.onEvent(end(ts = 140L))

        assertEquals(1, mgr.messages.value.size)
    }

    // === 空 streaming flush ===

    @Test
    fun `flush empty streaming produces no message`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(end(ts = 130L))

        assertTrue(mgr.messages.value.isEmpty())
    }

    @Test
    fun `start then user_message with no chunks does not produce empty assistant`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(userMsg("hi", ts = 200L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals(DmRole.USER, msgs[0].role)
    }

    // === reset ===

    @Test
    fun `reset clears messages and streamingMessage`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("hello", ts = 110L))
        mgr.onEvent(end(ts = 130L))
        mgr.onEvent(start(ts = 200L, msgId = "msg2"))
        mgr.onEvent(chunk("world", ts = 210L))

        mgr.reset()

        assertTrue(mgr.messages.value.isEmpty())
        assertNull(mgr.streamingMessage.value)
    }

    @Test
    fun `events after reset start fresh`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("old", ts = 110L))
        mgr.onEvent(end(ts = 130L))

        mgr.reset()

        mgr.onEvent(start(ts = 300L, msgId = "msg2"))
        mgr.onEvent(chunk("new", ts = 310L))
        mgr.onEvent(end(ts = 320L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals("new", msgs[0].content)
    }

    // === StateFlow 语义 ===

    @Test
    fun `messages value updates after each finalized message`() {
        val mgr = manager()

        mgr.onEvent(start(ts = 100L, msgId = "msg1"))
        mgr.onEvent(chunk("a", ts = 110L))
        mgr.onEvent(end(ts = 120L))
        assertEquals(1, mgr.messages.value.size)

        mgr.onEvent(start(ts = 200L, msgId = "msg2"))
        mgr.onEvent(chunk("b", ts = 210L))
        mgr.onEvent(end(ts = 220L))
        assertEquals(2, mgr.messages.value.size)
    }

    @Test
    fun `streamingMessage value updates on each chunk`() {
        val mgr = manager()

        assertNull(mgr.streamingMessage.value)

        mgr.onEvent(start())
        mgr.onEvent(chunk("a", ts = 110L))
        assertEquals("a", mgr.streamingMessage.value!!.content)

        mgr.onEvent(chunk("b", ts = 120L))
        assertEquals("ab", mgr.streamingMessage.value!!.content)
    }

    // === 仅 thinking/tool_call 的 flush（P0 场景）===

    @Test
    fun `thinking-only stream produces message on end`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(thoughtChunk("let me think", ts = 110L))
        mgr.onEvent(end(ts = 130L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].content.isNotBlank(), "Content should have fallback from thinking text")
        assertTrue(msgs[0].blocks.any { it is ContentBlock.Thinking })
    }

    @Test
    fun `tool_call-only stream produces message on end`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(toolCall(ts = 110L))
        mgr.onEvent(end(ts = 130L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertTrue(msgs[0].content.isNotBlank(), "Content should have fallback from tool_call")
        assertTrue(msgs[0].blocks.any { it is ContentBlock.ToolCall })
    }

    // === Batch 模式 ===

    @Test
    fun `batch mode suppresses messages updates during replay`() {
        val mgr = manager()

        mgr.beginBatch()
        mgr.onEvent(userMsg("q1", ts = 100L, msgId = "u1"))
        mgr.onEvent(end(fallback = "a1", ts = 200L))
        mgr.onEvent(userMsg("q2", ts = 300L, msgId = "u2"))
        mgr.onEvent(end(fallback = "a2", ts = 400L))

        // During batch: messages StateFlow should still be empty
        assertTrue(mgr.messages.value.isEmpty(), "messages should not update during batch")

        mgr.endBatch()

        // After endBatch: all messages appear at once
        assertEquals(4, mgr.messages.value.size)
        assertEquals(DmRole.USER, mgr.messages.value[0].role)
        assertEquals("q1", mgr.messages.value[0].content)
        assertEquals(DmRole.ASSISTANT, mgr.messages.value[1].role)
        assertEquals("a1", mgr.messages.value[1].content)
    }

    @Test
    fun `batch mode suppresses streamingMessage updates`() {
        val mgr = manager()

        mgr.beginBatch()
        mgr.onEvent(start())
        mgr.onEvent(chunk("hello", ts = 110L))

        assertNull(mgr.streamingMessage.value, "streamingMessage should not update during batch")

        mgr.onEvent(end(ts = 130L))
        mgr.endBatch()

        assertEquals(1, mgr.messages.value.size)
        assertEquals("hello", mgr.messages.value[0].content)
        assertNull(mgr.streamingMessage.value)
    }

    @Test
    fun `endBatch without beginBatch is safe no-op`() {
        val mgr = manager()

        mgr.onEvent(userMsg("hi", ts = 100L))
        mgr.endBatch() // should not crash

        assertEquals(1, mgr.messages.value.size)
    }

    @Test
    fun `events after endBatch emit normally`() {
        val mgr = manager()

        mgr.beginBatch()
        mgr.onEvent(end(fallback = "batched", ts = 100L))
        mgr.endBatch()

        mgr.onEvent(start(ts = 200L, msgId = "msg2"))
        mgr.onEvent(chunk("live", ts = 210L))
        assertNotNull(mgr.streamingMessage.value, "streaming should work after batch ends")
        assertEquals("live", mgr.streamingMessage.value!!.content)
    }

    // === replaceHistory (subscribe/resubscribe snapshot) ===

    private fun snapshotMessage(
        id: String = "m1",
        role: DmRole = DmRole.ASSISTANT,
        content: String = "hi",
        ts: Long = 100L,
        nodeId: String = "n1",
        nodeName: String = "bot",
    ) = DmMessage(
        id = id,
        role = role,
        content = content,
        timestamp = ts,
        nodeId = nodeId,
        nodeName = nodeName,
        blocks = listOf(ContentBlock.Text(content)),
    )

    @Test
    fun `replaceHistory replaces messages with snapshot`() {
        val mgr = manager()

        // pre-fill with stale data
        mgr.onEvent(userMsg("stale", ts = 100L))
        assertEquals(1, mgr.messages.value.size)

        mgr.replaceHistory(listOf(
            snapshotMessage(id = "s1", role = DmRole.USER, content = "hello", ts = 1000L),
            snapshotMessage(id = "s2", role = DmRole.ASSISTANT, content = "hi back", ts = 2000L),
        ))

        val msgs = mgr.messages.value
        assertEquals(2, msgs.size)
        assertEquals("hello", msgs[0].content)
        assertEquals("hi back", msgs[1].content)
    }

    @Test
    fun `replaceHistory with empty list clears messages`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(chunk("partial", ts = 110L))
        assertNotNull(mgr.streamingMessage.value)

        mgr.replaceHistory(emptyList())

        assertEquals(0, mgr.messages.value.size)
        assertNull(mgr.streamingMessage.value, "streaming cleared by replaceHistory")
    }

    @Test
    fun `replaceHistory discards pending batch and exits batch mode`() {
        val mgr = manager()

        mgr.beginBatch()
        mgr.onEvent(userMsg("should be discarded", ts = 100L))
        // batch not yet ended; message hidden in pending

        mgr.replaceHistory(listOf(snapshotMessage(content = "authoritative")))

        assertEquals(1, mgr.messages.value.size)
        assertEquals("authoritative", mgr.messages.value[0].content)

        // subsequent events emit immediately (batch was cleared)
        mgr.onEvent(start(ts = 200L, msgId = "live"))
        mgr.onEvent(chunk("live reply", ts = 210L))
        assertNotNull(mgr.streamingMessage.value)
    }

    // === Replay 场景 ===

    @Test
    fun `end with fallbackText and no active stream creates message`() {
        val mgr = manager()

        mgr.onEvent(end(fallback = "hello", ts = 130L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals(DmRole.ASSISTANT, msgs[0].role)
        assertEquals("hello", msgs[0].content)
    }

    @Test
    fun `end with fallbackText and thinking-only stream uses fallbackText for content`() {
        val mgr = manager()

        mgr.onEvent(start())
        mgr.onEvent(thoughtChunk("hmm", ts = 110L))
        mgr.onEvent(end(fallback = "answer", ts = 130L))

        val msgs = mgr.messages.value
        assertEquals(1, msgs.size)
        assertEquals("answer", msgs[0].content)
        assertTrue(msgs[0].blocks.any { it is ContentBlock.Thinking })
    }

    // === Summary Mode (textContent) ===

    @Test
    fun `textContent returns text blocks only when blocks present`() {
        val msg = DmMessage(
            id = "m1", role = DmRole.ASSISTANT, content = "fallback",
            timestamp = 100L, nodeId = "n1", nodeName = "bot",
            blocks = listOf(
                ContentBlock.Thinking("hmm", completed = true),
                ContentBlock.Text("answer"),
                ContentBlock.ToolCall("1", "bash", "ls"),
            ),
        )
        assertEquals("answer", msg.textContent)
    }

    @Test
    fun `textContent returns content when no blocks`() {
        val msg = DmMessage(
            id = "m1", role = DmRole.ASSISTANT, content = "hello",
            timestamp = 100L, nodeId = "n1", nodeName = "bot",
        )
        assertEquals("hello", msg.textContent)
    }

    @Test
    fun `textContent returns empty when only thinking and tool blocks`() {
        val msg = DmMessage(
            id = "m1", role = DmRole.ASSISTANT, content = "[thinking] hmm",
            timestamp = 100L, nodeId = "n1", nodeName = "bot",
            blocks = listOf(
                ContentBlock.Thinking("hmm", completed = true),
                ContentBlock.ToolCall("1", "bash", "ls"),
            ),
        )
        assertEquals("", msg.textContent)
    }

    @Test
    fun `textContent concatenates multiple text blocks`() {
        val msg = DmMessage(
            id = "m1", role = DmRole.ASSISTANT, content = "fallback",
            timestamp = 100L, nodeId = "n1", nodeName = "bot",
            blocks = listOf(
                ContentBlock.Text("hello"),
                ContentBlock.Thinking("hmm", completed = true),
                ContentBlock.Text(" world"),
            ),
        )
        assertEquals("hello world", msg.textContent)
    }

    @Test
    fun `finalized message with text chunks has clean textContent`() {
        val mgr = manager()
        mgr.onEvent(start())
        mgr.onEvent(thoughtChunk("let me think", ts = 105L))
        mgr.onEvent(chunk("the answer", ts = 110L))
        mgr.onEvent(toolCall(ts = 115L))
        mgr.onEvent(end(ts = 130L))

        val msg = mgr.messages.value.last()
        assertEquals("the answer", msg.textContent)
    }

    @Test
    fun `replay sequence without end between turns`() {
        val mgr = manager()

        mgr.onEvent(start(ts = 100L, msgId = "msg1"))
        mgr.onEvent(chunk("reply1", ts = 110L))
        mgr.onEvent(userMsg("q2", ts = 200L))
        mgr.onEvent(start(ts = 300L, msgId = "msg2"))
        mgr.onEvent(chunk("reply2", ts = 310L))
        mgr.onEvent(end(ts = 320L))

        val msgs = mgr.messages.value
        assertEquals(3, msgs.size)
        assertEquals(DmRole.ASSISTANT, msgs[0].role)
        assertEquals("reply1", msgs[0].content)
        assertEquals(DmRole.USER, msgs[1].role)
        assertEquals("q2", msgs[1].content)
        assertEquals(DmRole.ASSISTANT, msgs[2].role)
        assertEquals("reply2", msgs[2].content)
    }
}
