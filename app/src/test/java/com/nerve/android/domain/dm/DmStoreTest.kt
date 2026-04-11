package com.nerve.android.domain.dm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DmStoreTest {
    private val keyA = DmKey("s1:n1")
    private val keyB = DmKey("s1:n2")

    @Test
    fun `append messages keeps append only flow`() {
        val store: DmStore = InMemoryDmStore()

        store.appendMessage(keyA, message("m1", "one"))
        store.appendMessage(keyA, message("m2", "two"))
        store.appendMessage(keyA, message("m3", "three"))

        assertEquals(listOf("m1", "m2", "m3"), store.messages(keyA).value.map { it.id })
    }

    @Test
    fun `same id only inserts once`() {
        val store: DmStore = InMemoryDmStore()

        assertTrue(store.appendMessage(keyA, message("m1", "one")))
        assertFalse(store.appendMessage(keyA, message("m1", "one replay")))

        assertEquals(1, store.messages(keyA).value.size)
        assertTrue(store.containsMessage(keyA, "m1"))
    }

    @Test
    fun `different keys are isolated`() {
        val store: DmStore = InMemoryDmStore()

        store.appendMessage(keyA, message("m1", "one"))
        store.appendMessage(keyB, message("m2", "two"))

        assertEquals(listOf("m1"), store.messages(keyA).value.map { it.id })
        assertEquals(listOf("m2"), store.messages(keyB).value.map { it.id })
    }

    @Test
    fun `api exposes no clear reset remove`() {
        val names =
            (DmStore::class.java.methods.map { it.name } + InMemoryDmStore::class.java.methods.map { it.name }).toSet()

        assertFalse(names.contains("clear"))
        assertFalse(names.contains("reset"))
        assertFalse(names.contains("removeConversation"))
        assertFalse(names.contains("remove"))
    }

    private fun message(id: String, content: String) = DmMessage(
        id = id,
        role = DmRole.USER,
        content = content,
        timestamp = 1L,
        nodeId = "n1",
        nodeName = "bot",
    )
}
