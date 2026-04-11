package com.nerve.android.domain.channel

import com.nerve.android.transport.NerveEvent
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChannelEventMapperTest {
    private val mapper = DefaultChannelEventMapper()

    @Test
    fun `created and closed map to channel meta events`() {
        val created = assertIs<ChannelMappedEvent.ChannelCreated>(
            mapper.map(NerveEvent.ChannelCreated(channelId = "c1", name = "general")),
        )
        val closed = assertIs<ChannelMappedEvent.ChannelClosed>(
            mapper.map(NerveEvent.ChannelClosed(channelId = "c1", name = "general")),
        )

        assertEquals("c1", created.channelId)
        assertEquals("general", created.name)
        assertEquals("c1", closed.channelId)
        assertEquals("general", closed.name)
    }

    @Test
    fun `channel message parses with top level message id priority`() {
        val payload = buildJsonObject {
            put("ts", "2026-04-07T13:00:00Z")
            put(
                "message",
                buildJsonObject {
                    put("id", "payload-id")
                    put("from", "alice")
                    put("content", "hello")
                },
            )
        }

        val mapped = assertIs<ChannelMappedEvent.MessageReceived>(
            mapper.map(NerveEvent.ChannelMessage(channelId = "c1", messageId = "top-id", payload = payload)),
        )

        assertEquals("c1", mapped.channelId)
        assertEquals("top-id", mapped.message.id)
        assertEquals("alice", mapped.message.from)
        assertEquals("hello", mapped.message.content)
        assertEquals(1775566800000L, mapped.message.timestamp)
        assertEquals(payload, mapped.message.metadata)
    }

    @Test
    fun `channel mention uses same message model and payload id fallback`() {
        val payload = buildJsonObject {
            put(
                "message",
                buildJsonObject {
                    put("id", "payload-id")
                    put("nodeName", "bot")
                    put("text", "ping")
                    put("timestamp", "2026-04-07T13:00:01Z")
                },
            )
        }

        val mapped = assertIs<ChannelMappedEvent.MentionReceived>(
            mapper.map(NerveEvent.ChannelMention(channelId = "c1", payload = payload)),
        )

        assertEquals("payload-id", mapped.message.id)
        assertEquals("bot", mapped.message.from)
        assertEquals("ping", mapped.message.content)
        assertEquals(1775566801000L, mapped.message.timestamp)
    }

    @Test
    fun `channel mention falls back to stable id when payload id is missing`() {
        val payload = buildJsonObject {
            put("ts", "2026-04-07T13:00:03Z")
            put(
                "message",
                buildJsonObject {
                    put("from", "alice")
                    put("content", "ping")
                },
            )
        }

        val mapped = assertIs<ChannelMappedEvent.MentionReceived>(
            mapper.map(NerveEvent.ChannelMention(channelId = "c1", payload = payload)),
        )

        assertEquals("alice", mapped.message.from)
        assertEquals("ping", mapped.message.content)
        assertEquals("channel:c1:1775566803000:${"alice".hashCode()}:${"ping".hashCode()}", mapped.message.id)
    }

    @Test
    fun `message uses unknown when from and nodeName are both missing`() {
        val payload = buildJsonObject {
            put("ts", "2026-04-07T13:00:04Z")
            put(
                "message",
                buildJsonObject {
                    put("content", "hello")
                },
            )
        }

        val mapped = assertIs<ChannelMappedEvent.MessageReceived>(
            mapper.map(NerveEvent.ChannelMessage(channelId = "c1", messageId = "m2", payload = payload)),
        )

        assertEquals("unknown", mapped.message.from)
        assertEquals("hello", mapped.message.content)
    }

    @Test
    fun `missing content is ignored`() {
        val payload = buildJsonObject {
            put("from", "alice")
            put("ts", "2026-04-07T13:00:02Z")
        }

        assertIs<ChannelMappedEvent.Ignore>(
            mapper.map(NerveEvent.ChannelMessage(channelId = "c1", messageId = "m1", payload = payload)),
        )
    }
}
