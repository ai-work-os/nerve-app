package com.nerve.android.domain.channel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelStoreTest {
    @Test
    fun `append only store dedups by id and isolates keys`() = runTest {
        val store: ChannelStore = InMemoryChannelStore()
        val keyA = ChannelKey("s1:c1")
        val keyB = ChannelKey("s1:c2")

        assertTrue(store.appendMessage(keyA, message(id = "m1", channelId = "c1", content = "one", timestamp = 100L)))
        assertTrue(store.appendMessage(keyA, message(id = "m2", channelId = "c1", content = "two", timestamp = 200L)))
        assertFalse(store.appendMessage(keyA, message(id = "m2", channelId = "c1", content = "two", timestamp = 200L)))
        assertTrue(store.appendMessage(keyB, message(id = "m3", channelId = "c2", content = "other", timestamp = 300L)))

        assertEquals(listOf("m1", "m2"), store.messages(keyA).first().map { it.id })
        assertEquals(listOf("m3"), store.messages(keyB).first().map { it.id })
    }

    @Test
    fun `closed meta keeps history messages`() = runTest {
        val store: ChannelStore = InMemoryChannelStore()
        val key = ChannelKey("s1:c1")

        store.appendMessage(key, message(id = "m1", channelId = "c1", content = "persist", timestamp = 100L))
        store.upsertMeta(key, ChannelMeta(channelId = "c1", name = "general", isClosed = true))

        assertEquals(listOf("persist"), store.messages(key).first().map { it.content })
        assertEquals(true, store.meta(key).first()?.isClosed)
    }

    @Test
    fun `store api does not expose clear reset or remove messages`() {
        val methodNames = ChannelStore::class.declaredFunctions.map { it.name }.toSet()

        assertFalse("clear" in methodNames)
        assertFalse("reset" in methodNames)
        assertFalse("removeChannelMessages" in methodNames)
    }

    private fun message(id: String, channelId: String, content: String, timestamp: Long) = ChannelMessage(
        id = id,
        channelId = channelId,
        from = "alice",
        content = content,
        timestamp = timestamp,
        metadata = null,
    )
}
