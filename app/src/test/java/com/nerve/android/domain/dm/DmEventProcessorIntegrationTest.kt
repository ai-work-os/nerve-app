package com.nerve.android.domain.dm

import com.nerve.android.transport.NerveEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DmEventProcessorIntegrationTest {
    private val key = DmKey("s1:n1")

    @Test
    fun `full sequence stores user then assistant`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        processor.attach(
            serverId = "s1",
            nodeId = "n1",
            events = flowOf(
                user("hello", "2026-04-07T10:00:01Z"),
                start("2026-04-07T10:00:02Z"),
                chunk("hi ", "2026-04-07T10:00:03Z"),
                chunk("there", "2026-04-07T10:00:04Z"),
                end("2026-04-07T10:00:05Z"),
            ),
        )

        val messages = store.messages(key).value
        assertEquals(listOf(DmRole.USER, DmRole.ASSISTANT), messages.map { it.role })
        assertEquals(listOf("hello", "hi there"), messages.map { it.content })
    }

    @Test
    fun `replay same sequence twice stays deduped`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)
        val sequence = flowOf(
            user("hello", "2026-04-07T10:00:01Z"),
            start("2026-04-07T10:00:02Z"),
            chunk("hi", "2026-04-07T10:00:03Z"),
            end("2026-04-07T10:00:04Z"),
        )

        processor.attach("s1", "n1", sequence)
        processor.attach("s1", "n1", sequence)

        assertEquals(2, store.messages(key).value.size)
    }

    @Test
    fun `user message flushes unfinished assistant first`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        processor.attach(
            "s1",
            "n1",
            flowOf(
                start("2026-04-07T10:00:02Z"),
                chunk("draft", "2026-04-07T10:00:03Z"),
                user("next", "2026-04-07T10:00:04Z"),
            ),
        )

        val messages = store.messages(key).value
        assertEquals(listOf("draft", "next"), messages.map { it.content })
        assertEquals(listOf(DmRole.ASSISTANT, DmRole.USER), messages.map { it.role })
    }

    @Test
    fun `idle flushes chunk only assistant`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        processor.attach(
            "s1",
            "n1",
            flowOf(
                start("2026-04-07T10:00:02Z"),
                chunk("draft", "2026-04-07T10:00:03Z"),
                idle("2026-04-07T10:00:04Z"),
            ),
        )

        val assistant = store.messages(key).value.single()
        assertEquals(DmRole.ASSISTANT, assistant.role)
        assertEquals("draft", assistant.content)
        assertEquals(1775556004000L, assistant.timestamp)
    }

    @Test
    fun `reconnect new flow can finish previous stream`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        processor.attach(
            "s1",
            "n1",
            flowOf(
                user("hello", "2026-04-07T10:00:01Z"),
                start("2026-04-07T10:00:02Z"),
                chunk("hi", "2026-04-07T10:00:03Z"),
                chunk(" there", "2026-04-07T10:00:03Z"),
            ),
        )

        processor.attach(
            "s1",
            "n1",
            flowOf(idle("2026-04-07T10:00:04Z")),
        )

        val messages = store.messages(key).value
        assertEquals(2, messages.size)
        assertEquals("hi there", messages[1].content)
        assertEquals(1775556004000L, messages[1].timestamp)
    }

    @Test
    fun `concurrent attach both process events independently`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        val first = backgroundScope.async {
            processor.attach(
                "s1",
                "n1",
                flow {
                    emit(user("hello", "2026-04-07T10:00:01Z"))
                    awaitCancellation()
                },
            )
        }
        val second = async { processor.attach("s1", "n1", flowOf(user("world", "2026-04-07T10:00:09Z"))) }

        advanceUntilIdle()
        assertTrue(second.isCompleted)
        // Both attaches process — dedup is caller's responsibility (ChatViewModel cancels old attachJob)
        assertEquals(2, store.messages(key).value.size)

        first.cancel()
    }

    @Test
    fun `rapid reattach same key processes events in second attach`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        // First attach completes with one user message
        processor.attach(
            "s1",
            "n1",
            flowOf(user("hello", "2026-04-07T10:00:01Z")),
        )
        assertEquals(1, store.messages(key).value.size)

        // Second attach on same key processes normally (no activeKeys rejection)
        processor.attach(
            "s1",
            "n1",
            flowOf(
                start("2026-04-07T10:00:10Z"),
                chunk("reply", "2026-04-07T10:00:11Z"),
                end("2026-04-07T10:00:12Z"),
            ),
        )

        val messages = store.messages(key).value
        assertTrue(
            messages.any { it.role == DmRole.ASSISTANT && it.content == "reply" },
            "Second attach should have processed events, but messages=$messages",
        )
    }

    @Test
    fun `thought chunks route to accumulator`() = runTest {
        val store = InMemoryDmStore()
        val accumulator = InMemoryStreamingAccumulator()
        val processor = DefaultDmEventProcessor(store, accumulator = accumulator)

        processor.attach(
            "s1",
            "n1",
            flowOf(
                start("2026-04-07T10:00:02Z"),
                thought("let me", "2026-04-07T10:00:03Z"),
                thought(" think", "2026-04-07T10:00:04Z"),
            ),
        )

        val blocks = accumulator.currentBlocks(key)
        assertEquals(1, blocks.size)
        assertIs<ContentBlock.Thinking>(blocks[0])
        assertEquals("let me think", (blocks[0] as ContentBlock.Thinking).text)
    }

    @Test
    fun `tool_call routes to accumulator`() = runTest {
        val store = InMemoryDmStore()
        val accumulator = InMemoryStreamingAccumulator()
        val processor = DefaultDmEventProcessor(store, accumulator = accumulator)

        processor.attach(
            "s1",
            "n1",
            flowOf(
                start("2026-04-07T10:00:02Z"),
                toolCall("tc1", "bash", "{\"cmd\":\"ls\"}", "2026-04-07T10:00:03Z"),
            ),
        )

        val blocks = accumulator.currentBlocks(key)
        assertEquals(1, blocks.size)
        val block = assertIs<ContentBlock.ToolCall>(blocks[0])
        assertEquals("tc1", block.toolId)
        assertEquals("bash", block.toolName)
    }

    @Test
    fun `full stream with thinking preserves all blocks`() = runTest {
        val store = InMemoryDmStore()
        val accumulator = InMemoryStreamingAccumulator()
        val processor = DefaultDmEventProcessor(store, accumulator = accumulator)

        processor.attach(
            "s1",
            "n1",
            flowOf(
                start("2026-04-07T10:00:02Z"),
                thought("hmm", "2026-04-07T10:00:03Z"),
                chunk("answer", "2026-04-07T10:00:04Z"),
                end("2026-04-07T10:00:05Z"),
            ),
        )

        val messages = store.messages(key).value
        assertEquals(1, messages.size)
        assertEquals(2, messages[0].blocks.size)
        assertIs<ContentBlock.Thinking>(messages[0].blocks[0])
        val textBlock = assertIs<ContentBlock.Text>(messages[0].blocks[1])
        assertEquals("answer", textBlock.text)
        assertEquals("answer", messages[0].content)
    }

    @Test
    fun `malformed event does not crash processor`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        // "update" is a string instead of JsonObject — will cause jsonObject cast to throw
        val malformed = NerveEvent.NodeUpdate(
            nodeId = "n1",
            name = "bot",
            detail = buildJsonObject {
                put("ts", "2026-04-07T10:00:01Z")
                put("update", JsonPrimitive("not_an_object"))
            },
        )

        processor.attach(
            "s1",
            "n1",
            flowOf(
                malformed,
                user("hello", "2026-04-07T10:00:02Z"),
            ),
        )

        // Processor should survive the malformed event and process the user message
        val messages = store.messages(key).value
        assertEquals(1, messages.size, "Expected user message to be processed after malformed event")
        assertEquals("hello", messages[0].content)
    }

    @Test
    fun `processor continues after single event failure`() = runTest {
        val store = InMemoryDmStore()
        val processor = DefaultDmEventProcessor(store)

        val malformed = NerveEvent.NodeUpdate(
            nodeId = "n1",
            name = "bot",
            detail = buildJsonObject {
                put("ts", "2026-04-07T10:00:02Z")
                put("update", JsonPrimitive("broken"))
            },
        )

        processor.attach(
            "s1",
            "n1",
            flowOf(
                user("first", "2026-04-07T10:00:01Z"),
                malformed,
                user("third", "2026-04-07T10:00:03Z"),
            ),
        )

        val messages = store.messages(key).value
        assertEquals(2, messages.size, "Expected both valid messages, got: ${messages.map { it.content }}")
        assertEquals("first", messages[0].content)
        assertEquals("third", messages[1].content)
    }

    private fun user(text: String, ts: String) = update("user_message", ts, text)

    private fun start(ts: String) = update("agent_message_start", ts)

    private fun chunk(text: String, ts: String) = update("agent_message_chunk", ts, text)

    private fun end(ts: String, text: String? = null) = update("agent_message_end", ts, text)

    private fun thought(text: String, ts: String) = NerveEvent.NodeUpdate(
        nodeId = "n1",
        name = "bot",
        detail = buildJsonObject {
            put("ts", ts)
            put("update", buildJsonObject {
                put("sessionUpdate", "agent_thought_chunk")
                put("content", buildJsonObject {
                    put("type", "text")
                    put("text", text)
                })
            })
        },
    )

    private fun toolCall(id: String, name: String, input: String, ts: String) = NerveEvent.NodeUpdate(
        nodeId = "n1",
        name = "bot",
        detail = buildJsonObject {
            put("ts", ts)
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call")
                put("content", buildJsonObject {
                    put("id", id)
                    put("name", name)
                    put("input", buildJsonObject { put("raw", input) })
                })
            })
        },
    )

    private fun idle(ts: String) = NerveEvent.NodeStatusChanged(
        nodeId = "n1",
        status = "idle",
        detail = buildJsonObject { put("ts", ts) },
    )

    private fun update(kind: String, ts: String, text: String? = null) = NerveEvent.NodeUpdate(
        nodeId = "n1",
        name = "bot",
        detail = buildJsonObject {
            put("ts", ts)
            put(
                "update",
                buildJsonObject {
                    put("sessionUpdate", kind)
                    text?.let {
                        put(
                            "content",
                            buildJsonObject {
                                put("type", "text")
                                put("text", it)
                            },
                        )
                    }
                },
            )
        },
    )
}
