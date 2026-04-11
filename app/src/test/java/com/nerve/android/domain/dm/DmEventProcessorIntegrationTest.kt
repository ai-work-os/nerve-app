package com.nerve.android.domain.dm

import com.nerve.android.transport.NerveEvent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
    fun `duplicate concurrent attach does not double write`() = runTest {
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
        val second = async { processor.attach("s1", "n1", flowOf(user("ignored", "2026-04-07T10:00:09Z"))) }

        advanceUntilIdle()
        assertTrue(second.isCompleted)
        assertEquals(1, store.messages(key).value.size)

        first.cancel()
    }

    private fun user(text: String, ts: String) = update("user_message", ts, text)

    private fun start(ts: String) = update("agent_message_start", ts)

    private fun chunk(text: String, ts: String) = update("agent_message_chunk", ts, text)

    private fun end(ts: String, text: String? = null) = update("agent_message_end", ts, text)

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
