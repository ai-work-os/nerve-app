package com.nerve.android.domain.dm

import com.nerve.android.transport.NerveEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun `agent_thought_chunk maps to AgentThoughtChunk`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:06Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "agent_thought_chunk")
                put("content", buildJsonObject {
                    put("type", "text")
                    put("text", "let me think...")
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.AgentThoughtChunk>(event)
        assertEquals("n1", mapped.nodeId)
        assertEquals("let me think...", mapped.text)
        assertEquals(1775556006000L, mapped.timestamp)
    }

    @Test
    fun `agent_thought_end maps to AgentThoughtEnd`() {
        val event = mapper.map(
            nodeUpdate(updateDetail("agent_thought_end", "2026-04-07T10:00:07Z")),
        )

        val mapped = assertIs<DmMappedEvent.AgentThoughtEnd>(event)
        assertEquals("n1", mapped.nodeId)
        assertEquals(1775556007000L, mapped.timestamp)
    }

    @Test
    fun `tool_call maps to ToolCall`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:08Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call")
                put("content", buildJsonObject {
                    put("id", "tc_001")
                    put("name", "bash")
                    put("input", buildJsonObject { put("command", "ls -la") })
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.ToolCall>(event)
        assertEquals("n1", mapped.nodeId)
        assertEquals("tc_001", mapped.toolId)
        assertEquals("bash", mapped.toolName)
        assertEquals("{\"command\":\"ls -la\"}", mapped.input)
        assertEquals(1775556008000L, mapped.timestamp)
    }

    @Test
    fun `tool_call_update maps to ToolCallUpdate`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:09Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call_update")
                put("content", buildJsonObject {
                    put("id", "tc_001")
                    put("status", "completed")
                    put("output", "file1.txt\nfile2.txt")
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.ToolCallUpdate>(event)
        assertEquals("n1", mapped.nodeId)
        assertEquals("tc_001", mapped.toolId)
        assertEquals("completed", mapped.status)
        assertEquals("file1.txt\nfile2.txt", mapped.output)
        assertEquals(1775556009000L, mapped.timestamp)
    }

    @Test
    fun `empty thought chunk maps to Ignore`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:10Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "agent_thought_chunk")
                put("content", buildJsonObject {
                    put("type", "text")
                    put("text", "")
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        assertIs<DmMappedEvent.Ignore>(event)
    }

    @Test
    fun `tool_call with JsonArray content does not crash`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:11Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "some tool info")
                    })
                })
            })
        }

        // Should not throw — JsonArray content must be handled safely
        val event = mapper.map(nodeUpdate(detail))
        // Either maps to ToolCall with safe defaults or Ignore — must not crash
        assertTrue(event is DmMappedEvent.ToolCall || event is DmMappedEvent.Ignore,
            "Expected ToolCall or Ignore for JsonArray content, got $event")
    }

    @Test
    fun `tool_call_update with JsonArray content does not crash`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:12Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call_update")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", "output data")
                    })
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        assertTrue(event is DmMappedEvent.ToolCallUpdate || event is DmMappedEvent.Ignore,
            "Expected ToolCallUpdate or Ignore for JsonArray content, got $event")
    }

    @Test
    fun `tool_call with null content maps correctly`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:13Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call")
                // no content field at all
            })
        }

        // Should not crash — missing content should be handled gracefully
        val event = mapper.map(nodeUpdate(detail))
        assertTrue(event is DmMappedEvent.ToolCall || event is DmMappedEvent.Ignore,
            "Expected ToolCall or Ignore for null content, got $event")
    }

    @Test
    fun `unknown update is ignored`() {
        val event = mapper.map(nodeUpdate(updateDetail("some_future_type", "2026-04-07T10:00:14Z")))
        assertIs<DmMappedEvent.Ignore>(event)
    }

    // --- tool_call dual format tests ---

    @Test
    fun `tool_call ACP flat format maps correctly`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:15Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call")
                put("toolCallId", "toolu_abc123")
                put("title", "Read file.txt")
                put("kind", "tool_use")
                put("_meta", buildJsonObject {
                    put("claudeCode", buildJsonObject {
                        put("toolName", "Read")
                    })
                })
                put("rawInput", "{\"file_path\": \"/path/to/file\"}")
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.ToolCall>(event)
        assertEquals("toolu_abc123", mapped.toolId)
        assertEquals("Read", mapped.toolName)
        assertEquals("{\"file_path\": \"/path/to/file\"}", mapped.input)
        assertEquals(1775556015000L, mapped.timestamp)
    }

    @Test
    fun `tool_call legacy nested format maps correctly`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:16Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call")
                put("toolCall", buildJsonObject {
                    put("id", "tc_legacy_1")
                    put("name", "Bash")
                    put("input", buildJsonObject { put("command", "ls -la") })
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.ToolCall>(event)
        assertEquals("tc_legacy_1", mapped.toolId)
        assertEquals("Bash", mapped.toolName)
        assertEquals("{\"command\":\"ls -la\"}", mapped.input)
        assertEquals(1775556016000L, mapped.timestamp)
    }

    @Test
    fun `tool_call_update ACP flat format maps correctly`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:17Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call_update")
                put("toolCallId", "toolu_abc123")
                put("status", "completed")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "content")
                        put("content", buildJsonObject {
                            put("type", "text")
                            put("text", "file contents here...")
                        })
                    })
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.ToolCallUpdate>(event)
        assertEquals("toolu_abc123", mapped.toolId)
        assertEquals("completed", mapped.status)
        assertEquals("file contents here...", mapped.output)
        assertEquals(1775556017000L, mapped.timestamp)
    }

    @Test
    fun `tool_call_update legacy nested format maps correctly`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:18Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "tool_call_update")
                put("toolCallUpdate", buildJsonObject {
                    put("id", "tc_legacy_1")
                    put("status", "completed")
                    put("content", "result text")
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.ToolCallUpdate>(event)
        assertEquals("tc_legacy_1", mapped.toolId)
        assertEquals("completed", mapped.status)
        assertEquals("result text", mapped.output)
        assertEquals(1775556018000L, mapped.timestamp)
    }

    @Test
    fun `extractText handles JsonArray with text block`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:07Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "agent_message_end")
                put("content", buildJsonArray {
                    add(buildJsonObject { put("type", "thinking"); put("text", "let me think...") })
                    add(buildJsonObject { put("type", "text"); put("text", "hello") })
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.AgentMessageEnd>(event)
        assertEquals("hello", mapped.fallbackText)
    }

    @Test
    fun `extractText handles JsonArray with no text block`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:08Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "agent_message_end")
                put("content", buildJsonArray {
                    add(buildJsonObject { put("type", "thinking"); put("text", "hmm") })
                    add(buildJsonObject { put("type", "tool_use"); put("name", "bash") })
                })
            })
        }

        val event = mapper.map(nodeUpdate(detail))
        val mapped = assertIs<DmMappedEvent.AgentMessageEnd>(event)
        assertEquals(null, mapped.fallbackText)
    }

    @Test
    fun `extractText handles unknown JsonElement gracefully`() {
        val detail = buildJsonObject {
            put("ts", "2026-04-07T10:00:09Z")
            put("update", buildJsonObject {
                put("sessionUpdate", "user_message")
                put("content", buildJsonArray { add(42) })
            })
        }

        // Should not crash — graceful fallback to Ignore or empty
        val event = mapper.map(nodeUpdate(detail))
        // With a non-text array, user_message text is null/blank → Ignore
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
