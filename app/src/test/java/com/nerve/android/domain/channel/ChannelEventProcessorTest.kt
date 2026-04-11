package com.nerve.android.domain.channel

import com.nerve.android.transport.NerveEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelEventProcessorTest {
    @Test
    fun `created message replay mention close and duplicate attach follow store rules`() = runTest {
        val events = MutableSharedFlow<NerveEvent>(extraBufferCapacity = 32)
        val store: ChannelStore = InMemoryChannelStore()
        val processor: ChannelEventProcessor = RealChannelEventProcessor(store, DefaultChannelEventMapper())
        val key = ChannelKey("s1:c1")

        processor.attach("s1", events)
        processor.attach("s1", events)

        events.emit(NerveEvent.ChannelCreated(channelId = "c1", name = "general"))
        events.emit(channelMessage(channelId = "c1", topLevelId = "m1", payloadId = "payload-1", from = "alice", content = "hello", ts = "2026-04-07T13:00:00Z"))
        events.emit(channelMessage(channelId = "c1", topLevelId = "m1", payloadId = "payload-1", from = "alice", content = "hello", ts = "2026-04-07T13:00:00Z"))
        events.emit(channelMention(channelId = "c1", payloadId = "m1", from = "alice", content = "hello", ts = "2026-04-07T13:00:00Z"))
        events.emit(NerveEvent.ChannelClosed(channelId = "c1", name = "general"))
        advanceUntilIdle()

        val messages = store.messages(key).value
        val meta = store.meta(key).value

        assertEquals(1, messages.size)
        assertEquals("hello", messages.single().content)
        assertEquals("general", meta?.name)
        assertEquals(true, meta?.isClosed)
    }

    @Test
    fun `closed without existing meta creates closed placeholder`() = runTest {
        val events = MutableSharedFlow<NerveEvent>(extraBufferCapacity = 8)
        val store: ChannelStore = InMemoryChannelStore()
        val processor: ChannelEventProcessor = RealChannelEventProcessor(store, DefaultChannelEventMapper())
        val key = ChannelKey("s1:c2")

        processor.attach("s1", events)
        events.emit(NerveEvent.ChannelClosed(channelId = "c2", name = null))
        advanceUntilIdle()

        val meta = store.meta(key).value
        assertEquals("c2", meta?.channelId)
        assertEquals(true, meta?.isClosed)
        assertEquals(null, meta?.name)
        assertEquals(0, store.messages(key).value.size)
    }

    private fun channelMessage(
        channelId: String,
        topLevelId: String,
        payloadId: String,
        from: String,
        content: String,
        ts: String,
    ) = NerveEvent.ChannelMessage(
        channelId = channelId,
        messageId = topLevelId,
        payload = buildJsonObject {
            put("ts", ts)
            put(
                "message",
                buildJsonObject {
                    put("id", payloadId)
                    put("from", from)
                    put("content", content)
                },
            )
        },
    )

    private fun channelMention(
        channelId: String,
        payloadId: String,
        from: String,
        content: String,
        ts: String,
    ) = NerveEvent.ChannelMention(
        channelId = channelId,
        payload = buildJsonObject {
            put("ts", ts)
            put(
                "message",
                buildJsonObject {
                    put("id", payloadId)
                    put("from", from)
                    put("content", content)
                },
            )
        },
    )
}
