package com.nerve.android.domain.dm

import com.nerve.android.transport.NerveEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DmEventMapperTest {
    private val mapper = DmEventMapper()

    @Test
    fun `agent message start maps with stable id`() {
        val detail = updateDetail("agent_message_start", "2026-04-07T10:00:00Z")

        val first = assertIs<DmMappedEvent.AgentMessageStart>(mapper.map(nodeUpdate(detail)))
        val second = assertIs<DmMappedEvent.AgentMessageStart>(mapper.map(nodeUpdate(detail)))

        assertEquals("n1", first.nodeId)
        assertEquals("bot", first.nodeName)
        assertEquals(1775556000000L, first.timestamp)
        assertEquals(first.messageId, second.messageId)
    }

    @Test
    fun `user message maps correctly`() {
        val event = mapper.map(
            nodeUpdate(
                updateDetail(
                    "user_message",
                    "2026-04-07T10:00:01Z",
                    "hello",
                ),
            ),
        )

        val mapped = assertIs<DmMappedEvent.UserMessage>(event)
        assertEquals("hello", mapped.content)
        assertEquals(1775556001000L, mapped.timestamp)
        assertEquals("bot", mapped.nodeName)
    }

    @Test
    fun `agent chunk maps correctly`() {
        val event = mapper.map(
            nodeUpdate(
                updateDetail(
                    "agent_message_chunk",
                    "2026-04-07T10:00:02Z",
                    "world",
                ),
            ),
        )

        val mapped = assertIs<DmMappedEvent.AgentMessageChunk>(event)
        assertEquals("world", mapped.text)
        assertEquals(1775556002000L, mapped.timestamp)
    }

    @Test
    fun `agent end maps fallback text`() {
        val event = mapper.map(
            nodeUpdate(
                updateDetail(
                    "agent_message_end",
                    "2026-04-07T10:00:03Z",
                    "done",
                ),
            ),
        )

        val mapped = assertIs<DmMappedEvent.AgentMessageEnd>(event)
        assertEquals("done", mapped.fallbackText)
        assertEquals(1775556003000L, mapped.timestamp)
    }

    @Test
    fun `idle status maps to node idle`() {
        val event = mapper.map(
            NerveEvent.NodeStatusChanged(
                nodeId = "n1",
                status = "idle",
                detail = buildJsonObject { put("ts", "2026-04-07T10:00:04Z") },
            ),
        )

        val mapped = assertIs<DmMappedEvent.NodeIdle>(event)
        assertEquals("n1", mapped.nodeId)
        assertEquals(1775556004000L, mapped.timestamp)
    }

    @Test
    fun `message id depends on node id ts and content`() {
        val first = assertIs<DmMappedEvent.UserMessage>(
            mapper.map(nodeUpdate(updateDetail("user_message", "2026-04-07T10:00:05Z", "same"))),
        )
        val same = assertIs<DmMappedEvent.UserMessage>(
            mapper.map(nodeUpdate(updateDetail("user_message", "2026-04-07T10:00:05Z", "same"))),
        )
        val changed = assertIs<DmMappedEvent.UserMessage>(
            mapper.map(nodeUpdate(updateDetail("user_message", "2026-04-07T10:00:05Z", "diff"))),
        )

        assertEquals(first.messageId, same.messageId)
        assertEquals(false, first.messageId == changed.messageId)
    }

    @Test
    fun `unknown update is ignored`() {
        val event = mapper.map(nodeUpdate(updateDetail("tool_call", "2026-04-07T10:00:06Z")))
        assertIs<DmMappedEvent.Ignore>(event)
    }

    private fun nodeUpdate(detail: kotlinx.serialization.json.JsonObject) =
        NerveEvent.NodeUpdate(nodeId = "n1", name = "bot", detail = detail)

    private fun updateDetail(sessionUpdate: String, ts: String, text: String? = null) = buildJsonObject {
        put("ts", ts)
        put(
            "update",
            buildJsonObject {
                put("sessionUpdate", sessionUpdate)
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
    }
}
