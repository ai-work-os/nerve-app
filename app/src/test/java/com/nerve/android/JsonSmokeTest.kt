package com.nerve.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JsonSmokeTest {
    @Serializable
    data class SmokePayload(
        val id: String,
        val text: String,
    )

    @Test
    fun jsonRoundTripWorks() {
        val payload = SmokePayload(id = "msg-1", text = "nerve-app")
        val encoded = Json.encodeToString(SmokePayload.serializer(), payload)
        val decoded = Json.decodeFromString(SmokePayload.serializer(), encoded)

        assertEquals(payload, decoded)
    }
}
